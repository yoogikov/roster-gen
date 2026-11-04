package com.iitm.roster.requests;

import com.iitm.roster.domain.Actor;
import com.iitm.roster.domain.ChangeRequest;
import com.iitm.roster.domain.RequestState;
import com.iitm.roster.domain.Roster;
import com.iitm.roster.domain.ShiftSlot;
import com.iitm.roster.domain.ShiftType;
import com.iitm.roster.domain.StandardShifts;
import com.iitm.roster.repo.ChangeRequestStore;
import com.iitm.roster.repo.RosterStore;
import com.iitm.roster.security.Action;
import com.iitm.roster.security.AuthorizationPolicy;
import com.iitm.roster.security.Decision;
import com.iitm.roster.service.EditCommand;
import com.iitm.roster.service.EditResult;
import com.iitm.roster.service.RosterService;
import java.time.Clock;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Edits that {@link Decision.RequiresApproval} turned into a request.
 * Approval applies the edit through {@link RosterService#applyEdit}.
 */
@Service
public class ChangeLifecycle {

	private final RosterService edits;
	private final RosterStore rosters;
	private final ChangeRequestStore requests;
	private final AuthorizationPolicy policy;
	private final Clock clock;

	public ChangeLifecycle(RosterService edits, RosterStore rosters, ChangeRequestStore requests,
			AuthorizationPolicy policy, Clock clock) {
		this.edits = edits;
		this.rosters = rosters;
		this.requests = requests;
		this.policy = policy;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<ChangeRequest> open() {
		return requests.findOpen();
	}

	/**
	 * Stores {@code command} as {@code PENDING}. The policy must say it needs approval;
	 * an allowed edit is applied directly, and a denied one is not stored.
	 */
	@Transactional
	public ChangeResult file(EditCommand command, Actor actor) {
		Objects.requireNonNull(command, "command");
		Objects.requireNonNull(actor, "actor");
		YearMonth month = YearMonth.from(command.slot().date());
		Roster roster = rosters.findByMonth(month)
				.orElseThrow(() -> new IllegalArgumentException("no roster for " + month));
		Decision decision = policy.may(actor, new Action.EditShift(command.slot(), staffIds(command)), roster);
		if (!(decision instanceof Decision.RequiresApproval)) {
			return new ChangeResult.NotAllowed(decision);
		}
		ChangeRequest request = request(command, month);
		requests.save(request);
		return new ChangeResult.Pending(request);
	}

	/** Applies a pending edit. If the rules reject it, the request stays pending and nothing is saved. */
	@Transactional
	public ChangeResult approve(String id, Actor actor) {
		ChangeRequest request = requirePending(id);
		ChangeResult denied = denial(actor, rosterOf(request));
		if (denied != null) {
			return denied;
		}
		EditResult edit = edits.applyEdit(toCommand(request), actor);
		if (edit instanceof EditResult.Rejected rejected) {
			return new ChangeResult.EditRejected(request, rejected);
		}
		ChangeRequest approved = request.withState(RequestState.APPROVED);
		requests.save(approved);
		return new ChangeResult.Approved(approved);
	}

	@Transactional
	public ChangeResult reject(String id, Actor actor) {
		ChangeRequest request = requirePending(id);
		ChangeResult denied = denial(actor, rosterOf(request));
		if (denied != null) {
			return denied;
		}
		ChangeRequest rejected = request.withState(RequestState.REJECTED);
		requests.save(rejected);
		return new ChangeResult.Rejected(rejected);
	}

	private ChangeResult denial(Actor actor, Roster roster) {
		Decision decision = policy.may(actor, new Action.DecideRequest(), roster);
		if (decision instanceof Decision.Allow) {
			return null;
		}
		return new ChangeResult.NotAllowed(decision);
	}

	private ChangeRequest requirePending(String id) {
		ChangeRequest request = requests.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("no change request " + id));
		if (request.state() != RequestState.PENDING) {
			throw new IllegalArgumentException("only a PENDING edit can be decided: " + request.state());
		}
		return request;
	}

	private Roster rosterOf(ChangeRequest request) {
		return rosters.findByMonth(request.month())
				.orElseThrow(() -> new IllegalArgumentException("no roster for " + request.month()));
	}

	private ChangeRequest request(EditCommand command, YearMonth month) {
		return switch (command) {
			case EditCommand.Assign assign -> new ChangeRequest(UUID.randomUUID().toString(), month, "assign",
					assign.slot().date(), assign.slot().shift().code(), assign.staffId(), null, null, assign.post(),
					RequestState.PENDING, clock.instant());
			case EditCommand.Unassign unassign -> new ChangeRequest(UUID.randomUUID().toString(), month, "unassign",
					unassign.slot().date(), unassign.slot().shift().code(), unassign.staffId(), null, null, null,
					RequestState.PENDING, clock.instant());
			case EditCommand.Move move -> new ChangeRequest(UUID.randomUUID().toString(), month, "move",
					move.slot().date(), move.slot().shift().code(), null, move.fromStaffId(), move.toStaffId(), null,
					RequestState.PENDING, clock.instant());
		};
	}

	private static Set<String> staffIds(EditCommand command) {
		return switch (command) {
			case EditCommand.Assign assign -> Set.of(assign.staffId());
			case EditCommand.Unassign unassign -> Set.of(unassign.staffId());
			case EditCommand.Move move -> Set.of(move.fromStaffId(), move.toStaffId());
		};
	}

	static EditCommand toCommand(ChangeRequest request) {
		ShiftSlot slot = new ShiftSlot(request.date(), shift(request.shiftCode()));
		return switch (request.type()) {
			case "assign" -> new EditCommand.Assign(slot, request.staffId(), request.post());
			case "unassign" -> new EditCommand.Unassign(slot, request.staffId());
			case "move" -> new EditCommand.Move(slot, request.fromStaffId(), request.toStaffId());
			default -> throw new IllegalArgumentException("unknown edit type: " + request.type());
		};
	}

	private static ShiftType shift(String code) {
		return switch (code) {
			case "I" -> StandardShifts.SHIFT_I;
			case "II" -> StandardShifts.SHIFT_II;
			case "N" -> StandardShifts.NIGHT;
			default -> throw new IllegalArgumentException("unknown shift: " + code);
		};
	}

}
