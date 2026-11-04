package com.iitm.roster.requests;

import com.iitm.roster.domain.Actor;
import com.iitm.roster.domain.LeaveRequest;
import com.iitm.roster.domain.RequestState;
import com.iitm.roster.domain.Roster;
import com.iitm.roster.domain.Staff;
import com.iitm.roster.repo.LeaveRequestStore;
import com.iitm.roster.repo.RosterStore;
import com.iitm.roster.repo.StaffStore;
import com.iitm.roster.security.Action;
import com.iitm.roster.security.AuthorizationPolicy;
import com.iitm.roster.security.Decision;
import com.iitm.roster.service.LeaveLedger;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Leave requests: propose, then an admin approves or rejects.
 * Approval writes the leave onto the month input, so rostering that doctor on those days is refused.
 */
@Service
@SuppressFBWarnings(value = "EI_EXPOSE_REP2",
		justification = "the stores and ledger are Spring singletons injected on purpose; sharing them is the point")
public class LeaveLifecycle {

	private final LeaveRequestStore requests;
	private final StaffStore staff;
	private final RosterStore rosters;
	private final LeaveLedger ledger;
	private final AuthorizationPolicy policy;
	private final Clock clock;

	public LeaveLifecycle(LeaveRequestStore requests, StaffStore staff, RosterStore rosters, LeaveLedger ledger,
			AuthorizationPolicy policy, Clock clock) {
		this.requests = requests;
		this.staff = staff;
		this.rosters = rosters;
		this.ledger = ledger;
		this.policy = policy;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<LeaveRequest> open() {
		return requests.findOpen();
	}

	@Transactional
	public LeaveResult propose(String staffId, LocalDate firstDay, LocalDate lastDay, Actor actor) {
		Objects.requireNonNull(actor, "actor");
		Staff doctor = staff.findById(staffId)
				.orElseThrow(() -> new IllegalArgumentException("unknown staff: " + staffId));
		YearMonth month = YearMonth.from(firstDay);
		Roster roster = rosters.findByMonth(month).orElseGet(() -> Roster.empty(month));
		Decision decision = policy.may(actor, new Action.ProposeLeave(staffId, firstDay), roster);
		if (!(decision instanceof Decision.Allow)) {
			return new LeaveResult.NotAllowed(decision);
		}
		if (!YearMonth.from(lastDay).equals(month)) {
			return new LeaveResult.Refused("leave must stay inside one month");
		}
		LeaveRequest request = new LeaveRequest(UUID.randomUUID().toString(), doctor, firstDay, lastDay,
				RequestState.PENDING, clock.instant());
		requests.save(request);
		return new LeaveResult.Pending(request);
	}

	@Transactional
	public LeaveResult approve(String id, Actor actor) {
		LeaveRequest request = requirePending(id);
		LeaveResult denied = denial(actor, request);
		if (denied != null) {
			return denied;
		}
		try {
			ledger.add(request.toLeave());
		} catch (IllegalArgumentException ex) {
			return new LeaveResult.Refused(ex.getMessage());
		}
		LeaveRequest approved = request.withState(RequestState.APPROVED);
		requests.save(approved);
		return new LeaveResult.Approved(approved);
	}

	@Transactional
	public LeaveResult reject(String id, Actor actor) {
		LeaveRequest request = requirePending(id);
		LeaveResult denied = denial(actor, request);
		if (denied != null) {
			return denied;
		}
		LeaveRequest rejected = request.withState(RequestState.REJECTED);
		requests.save(rejected);
		return new LeaveResult.Rejected(rejected);
	}

	private LeaveResult denial(Actor actor, LeaveRequest request) {
		Roster roster = rosters.findByMonth(YearMonth.from(request.firstDay()))
				.orElseGet(() -> Roster.empty(YearMonth.from(request.firstDay())));
		Decision decision = policy.may(actor, new Action.DecideRequest(), roster);
		if (decision instanceof Decision.Allow) {
			return null;
		}
		return new LeaveResult.NotAllowed(decision);
	}

	private LeaveRequest requirePending(String id) {
		LeaveRequest request = requests.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("no leave request " + id));
		if (request.state() != RequestState.PENDING) {
			throw new IllegalArgumentException("only a PENDING leave request can be decided: " + request.state());
		}
		return request;
	}

}
