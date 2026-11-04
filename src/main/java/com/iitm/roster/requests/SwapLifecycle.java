package com.iitm.roster.requests;

import com.iitm.roster.domain.Actor;
import com.iitm.roster.domain.Assignment;
import com.iitm.roster.domain.Leave;
import com.iitm.roster.domain.Post;
import com.iitm.roster.domain.RequestState;
import com.iitm.roster.domain.Roster;
import com.iitm.roster.domain.ShiftSlot;
import com.iitm.roster.domain.Staff;
import com.iitm.roster.domain.StandardShifts;
import com.iitm.roster.domain.SwapRequest;
import com.iitm.roster.repo.MonthInputStore;
import com.iitm.roster.repo.RosterStore;
import com.iitm.roster.repo.StoredMonthInput;
import com.iitm.roster.repo.SwapRequestStore;
import com.iitm.roster.scheduler.NightRotation;
import com.iitm.roster.security.Action;
import com.iitm.roster.security.AuthorizationPolicy;
import com.iitm.roster.security.Decision;
import com.iitm.roster.service.EditCommand;
import com.iitm.roster.service.EditResult;
import com.iitm.roster.service.RosterService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Swap requests: propose, accept, approve or reject, and lapse.
 *
 * <p>Approval moves both assignments through {@link RosterService#applyEdits}. A night moves
 * with that day's Shift I ward duty, because the night doctor is also the ward doctor.
 * Post-duty off is not stored; it is the day after a night, so it follows the night.
 * A night given away by someone on short leave (at most {@link NightRotation#LONG_LEAVE_DAYS}
 * days) can only go to the doctor {@link NightRotation} would have put there.
 *
 * <p>An open request whose earlier shift has started becomes {@code LAPSED}. The roster
 * is left untouched.
 */
@Service
public class SwapLifecycle {

	private final RosterService edits;
	private final RosterStore rosters;
	private final SwapRequestStore requests;
	private final MonthInputStore inputs;
	private final AuthorizationPolicy policy;
	private final Clock clock;

	public SwapLifecycle(RosterService edits, RosterStore rosters, SwapRequestStore requests, MonthInputStore inputs,
			AuthorizationPolicy policy, Clock clock) {
		this.edits = edits;
		this.rosters = rosters;
		this.requests = requests;
		this.inputs = inputs;
		this.policy = policy;
		this.clock = clock;
	}

	/** The stored request. Throws if the id is unknown. */
	@Transactional(readOnly = true)
	public SwapRequest get(String id) {
		return require(id);
	}

	/**
	 * Opens a {@code PENDING} request to exchange {@code offered} for {@code wanted}.
	 *
	 * @throws IllegalArgumentException if the two assignments are not both on the one month's roster
	 */
	@Transactional
	public SwapResult propose(Assignment offered, Assignment wanted, Actor actor) {
		Objects.requireNonNull(offered, "offered");
		Objects.requireNonNull(wanted, "wanted");
		Objects.requireNonNull(actor, "actor");
		Roster roster = rosterContaining(offered, wanted);
		SwapResult denied = denial(policy.may(actor, new Action.ProposeSwap(offered, wanted), roster));
		if (denied != null) {
			return denied;
		}
		Optional<String> shortLeave = shortLeaveProblem(offered, wanted);
		if (shortLeave.isPresent()) {
			return new SwapResult.Refused(shortLeave.get());
		}
		SwapRequest request = new SwapRequest(UUID.randomUUID().toString(), offered, wanted, RequestState.PENDING,
				clock.instant());
		requests.save(request);
		return new SwapResult.Pending(request);
	}

	/**
	 * The person asked agrees: {@code PENDING} becomes {@code ACCEPTED}.
	 *
	 * @throws IllegalArgumentException if there is no such request, or it is not pending
	 */
	@Transactional
	public SwapResult accept(String id, Actor actor) {
		SwapRequest request = requireOpen(id, RequestState.PENDING, "accepted");
		SwapResult denied = denial(policy.may(actor, new Action.AcceptSwap(request), rosterOf(request)));
		if (denied != null) {
			return denied;
		}
		SwapRequest accepted = request.withState(RequestState.ACCEPTED);
		requests.save(accepted);
		return new SwapResult.Accepted(accepted);
	}

	/**
	 * An admin approves an {@code ACCEPTED} swap. Both assignments move, a night also moves
	 * that day's Shift I ward duty, and the request becomes {@code APPROVED}. If the edit is
	 * rejected, the request stays {@code ACCEPTED} and the roster is unchanged.
	 *
	 * @throws IllegalArgumentException if there is no such request, it is not accepted, or a
	 *     night has no Shift I ward duty to move with it
	 */
	@Transactional
	public SwapResult approve(String id, Actor actor) {
		SwapRequest request = requireOpen(id, RequestState.ACCEPTED, "approved");
		Roster roster = rosterOf(request);
		SwapResult denied = denial(policy.may(actor, new Action.DecideSwap(request), roster));
		if (denied != null) {
			return denied;
		}
		EditResult edit = edits.applyEdits(moves(request, roster), actor);
		if (edit instanceof EditResult.Rejected rejected) {
			return new SwapResult.EditRejected(request, rejected);
		}
		SwapRequest approved = request.withState(RequestState.APPROVED);
		requests.save(approved);
		return new SwapResult.Approved(approved);
	}

	/**
	 * An admin rejects an open request. The roster is not changed.
	 *
	 * @throws IllegalArgumentException if there is no such request, or it is already final
	 */
	@Transactional
	public SwapResult reject(String id, Actor actor) {
		Objects.requireNonNull(actor, "actor");
		SwapRequest request = require(id);
		if (!request.state().isOpen()) {
			throw new IllegalArgumentException("a " + request.state() + " swap cannot be rejected");
		}
		SwapResult denied = denial(policy.may(actor, new Action.DecideSwap(request), rosterOf(request)));
		if (denied != null) {
			return denied;
		}
		SwapRequest rejected = request.withState(RequestState.REJECTED);
		requests.save(rejected);
		return new SwapResult.Rejected(rejected);
	}

	/**
	 * Marks every open request whose earlier shift has already started as {@code LAPSED}.
	 * Nothing is written to the roster or the audit log. "Already started" includes the
	 * instant the shift starts.
	 */
	@Transactional
	public List<SwapRequest> lapseOpen() {
		List<SwapRequest> lapsed = new ArrayList<>();
		for (SwapRequest request : requests.findOpen()) {
			if (started(request)) {
				SwapRequest updated = request.withState(RequestState.LAPSED);
				requests.save(updated);
				lapsed.add(updated);
			}
		}
		return List.copyOf(lapsed);
	}

	private SwapRequest requireOpen(String id, RequestState expected, String action) {
		SwapRequest request = require(id);
		if (request.state() != expected) {
			throw new IllegalArgumentException("only a " + expected + " swap can be " + action + ": " + request.state());
		}
		return request;
	}

	private SwapRequest require(String id) {
		if (id == null || id.isBlank()) {
			throw new IllegalArgumentException("id must not be blank");
		}
		return requests.findById(id).orElseThrow(() -> new IllegalArgumentException("no swap " + id));
	}

	private Roster rosterOf(SwapRequest request) {
		return rosterContaining(request.offered(), request.wanted());
	}

	private Roster rosterContaining(Assignment offered, Assignment wanted) {
		YearMonth month = YearMonth.from(offered.slot().date());
		if (!YearMonth.from(wanted.slot().date()).equals(month)) {
			throw new IllegalArgumentException("a swap must stay inside one month");
		}
		Roster roster = rosters.findByMonth(month)
				.orElseThrow(() -> new IllegalArgumentException("no roster for " + month));
		if (!roster.assignments().contains(offered) || !roster.assignments().contains(wanted)) {
			throw new IllegalArgumentException("both assignments must already be on the roster");
		}
		return roster;
	}

	/** The policy's answer, or null when the actor may proceed. */
	private static SwapResult denial(Decision decision) {
		if (decision instanceof Decision.Allow) {
			return null;
		}
		return new SwapResult.NotAllowed(decision);
	}

	/**
	 * Move each side. A night also moves that day's Shift I ward duty for the same doctor.
	 * The ward move is a second command, so it gets its own audit event.
	 */
	private static List<EditCommand> moves(SwapRequest request, Roster roster) {
		List<EditCommand> commands = new ArrayList<>();
		move(commands, roster, request.offered(), request.wanted().staff());
		move(commands, roster, request.wanted(), request.offered().staff());
		return List.copyOf(commands);
	}

	private static void move(List<EditCommand> commands, Roster roster, Assignment from, Staff to) {
		commands.add(new EditCommand.Move(from.slot(), from.staff().id(), to.id()));
		if (!from.slot().shift().equals(StandardShifts.NIGHT)) {
			return;
		}
		ShiftSlot wardSlot = new ShiftSlot(from.slot().date(), StandardShifts.SHIFT_I);
		Assignment ward = roster.assignmentsOn(wardSlot).stream()
				.filter(assignment -> assignment.staff().equals(from.staff()))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						from.staff().id() + " is on nights on " + from.slot().date() + " but has no Shift I duty"));
		if (ward.post() != Post.WARD) {
			throw new IllegalArgumentException(
					from.staff().id() + " is on nights on " + from.slot().date() + " but is not the ward doctor");
		}
		commands.add(new EditCommand.Move(wardSlot, from.staff().id(), to.id()));
	}

	/**
	 * When the doctor giving away a night is on short leave that day, the doctor taking it
	 * must be the one the rotation would assign. Months with no stored input are not checked
	 * (there is no rotation order to follow).
	 */
	private Optional<String> shortLeaveProblem(Assignment offered, Assignment wanted) {
		YearMonth month = YearMonth.from(offered.slot().date());
		Optional<StoredMonthInput> stored = inputs.findByMonth(month);
		if (stored.isEmpty()) {
			return Optional.empty();
		}
		StoredMonthInput input = stored.get();
		Map<LocalDate, Staff> planned = NightRotation.plan(input.month(), input.doctors(), input.firstOnNights(),
				input.leaves());
		Optional<String> offeredProblem = nightProblem(offered, wanted.staff(), input.leaves(), planned);
		if (offeredProblem.isPresent()) {
			return offeredProblem;
		}
		return nightProblem(wanted, offered.staff(), input.leaves(), planned);
	}

	private static Optional<String> nightProblem(Assignment night, Staff receiver, List<Leave> leaves,
			Map<LocalDate, Staff> planned) {
		if (!night.slot().shift().equals(StandardShifts.NIGHT) || !onShortLeave(night.staff(), night.slot().date(), leaves)) {
			return Optional.empty();
		}
		Staff expected = planned.get(night.slot().date());
		if (receiver.equals(expected)) {
			return Optional.empty();
		}
		String who = expected == null ? "nobody" : expected.id();
		return Optional.of("a short-leave night on " + night.slot().date() + " goes to " + who
				+ " in the rotation, not " + receiver.id());
	}

	/** Short leave is at most {@link NightRotation#LONG_LEAVE_DAYS} days, the same line the rotation uses. */
	private static boolean onShortLeave(Staff doctor, LocalDate day, List<Leave> leaves) {
		boolean covered = false;
		for (Leave leave : leaves) {
			if (!leave.staff().equals(doctor) || !leave.covers(day)) {
				continue;
			}
			if (ChronoUnit.DAYS.between(leave.firstDay(), leave.lastDay()) + 1 > NightRotation.LONG_LEAVE_DAYS) {
				return false;
			}
			covered = true;
		}
		return covered;
	}

	private boolean started(SwapRequest request) {
		Instant now = clock.instant();
		Instant offered = request.offered().slot().start().toInstant();
		Instant wanted = request.wanted().slot().start().toInstant();
		Instant earlier = offered.isBefore(wanted) ? offered : wanted;
		return !now.isBefore(earlier);
	}

}
