package com.iitm.roster.service;

import com.iitm.roster.constraints.Constraint;
import com.iitm.roster.constraints.Severity;
import com.iitm.roster.constraints.Violation;
import com.iitm.roster.domain.Actor;
import com.iitm.roster.domain.Assignment;
import com.iitm.roster.domain.AuditEvent;
import com.iitm.roster.domain.DayOff;
import com.iitm.roster.domain.Leave;
import com.iitm.roster.domain.Roster;
import com.iitm.roster.domain.RosterStatus;
import com.iitm.roster.domain.ShiftSlot;
import com.iitm.roster.domain.Staff;
import com.iitm.roster.repo.AuditStore;
import com.iitm.roster.repo.RosterStore;
import com.iitm.roster.repo.StaffStore;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only way a stored roster changes.
 *
 * <p>An edit is rejected if it double-books someone or adds a HARD violation. "Adds" means
 * that for some constraint and date there are more HARD violations after the edit than
 * before. Requiring none at all would block every edit to a half-filled roster, where
 * minimum staffing is broken everywhere. Comparing exact violations would also fail,
 * because a violation's message can change while it gets better ("needs 2, has 0" becomes
 * "has 1"). Counting per constraint and date still catches a fix on one rule hiding a new
 * break of another.
 */
@Service
@SuppressFBWarnings(value = "EI_EXPOSE_REP2",
		justification = "the stores are Spring singletons injected on purpose; sharing them is the point")
public class RosterService {

	/** Constraint id used for double-booking, which {@link Roster} refuses outright. */
	public static final String DOUBLE_BOOKING = "double-booking";

	private final RosterStore rosters;
	private final StaffStore staff;
	private final AuditStore audit;
	private final ConstraintSource constraints;
	private final Clock clock;

	public RosterService(RosterStore rosters, StaffStore staff, AuditStore audit, ConstraintSource constraints,
			Clock clock) {
		this.rosters = rosters;
		this.staff = staff;
		this.audit = audit;
		this.constraints = constraints;
		this.clock = clock;
	}

	/**
	 * Applies {@code command} for {@code actor} in one transaction: either the roster is
	 * saved and exactly one audit event is written, or nothing is stored.
	 *
	 * @throws IllegalArgumentException if the month has no roster, a staff id is unknown, or
	 *     the staff member to remove is not on the slot
	 */
	@Transactional
	public EditResult applyEdit(EditCommand command, Actor actor) {
		Objects.requireNonNull(command, "command");
		Objects.requireNonNull(actor, "actor");
		return applyEdits(List.of(command), actor);
	}

	/**
	 * Applies every command to one month, or none of them. A swap uses this so both
	 * assignments (and a night's Shift I ward duty) move in one transaction: the hospital
	 * rules are checked on the final roster only, and one audit event is written per command.
	 *
	 * <p>Removals happen first and additions afterwards. Shift I and Shift II overlap by the
	 * handover, so exchanging them would look like a double-booking in the middle even though
	 * each doctor ends up with only one of the two shifts.
	 *
	 * @throws IllegalArgumentException if {@code commands} is empty, the commands span two
	 *     months, the month has no roster, a staff id is unknown, or the staff member to
	 *     remove is not on the slot
	 */
	@Transactional
	public EditResult applyEdits(List<EditCommand> commands, Actor actor) {
		Objects.requireNonNull(commands, "commands");
		Objects.requireNonNull(actor, "actor");
		if (commands.isEmpty()) {
			throw new IllegalArgumentException("nothing to apply");
		}
		List<EditCommand> batch = List.copyOf(commands);
		YearMonth month = batch.get(0).month();
		for (EditCommand command : batch) {
			if (!command.month().equals(month)) {
				throw new IllegalArgumentException("edits must all fall in " + month);
			}
		}
		Roster before = rosters.findByMonth(month)
				.orElseThrow(() -> new IllegalArgumentException("no roster for " + month));

		Roster stripped = before;
		List<Assignment> toAdd = new ArrayList<>();
		List<String> summaries = new ArrayList<>();
		for (EditCommand command : batch) {
			Change change = plan(command, stripped);
			stripped = change.without();
			change.added().ifPresent(toAdd::add);
			summaries.add(change.summary());
		}

		Roster after = stripped;
		for (Assignment assignment : toAdd) {
			if (after.wouldDoubleBook(assignment)) {
				return new EditResult.Rejected(List.of(doubleBooking(assignment)));
			}
			after = after.withAssignment(assignment);
		}

		List<Constraint> active = constraints.constraintsFor(month);
		List<Violation> violationsBefore = check(active, before);
		List<Violation> violationsAfter = check(active, after);
		List<Violation> newHard = added(violationsBefore, violationsAfter, Severity.HARD);
		if (!newHard.isEmpty()) {
			return new EditResult.Rejected(newHard);
		}

		rosters.save(after);
		for (String summary : summaries) {
			audit.append(new AuditEvent(clock.instant(), actor.username(), month, summary));
		}
		return new EditResult.Applied(after, added(violationsBefore, violationsAfter, Severity.SOFT));
	}

	/**
	 * Marks the month published and writes one audit event. The caller has already asked
	 * {@code AuthorizationPolicy}.
	 *
	 * @throws IllegalArgumentException if the month has no roster
	 * @throws IllegalStateException if it is already published
	 */
	@Transactional
	public Roster publish(YearMonth month, Actor actor) {
		Objects.requireNonNull(month, "month");
		Objects.requireNonNull(actor, "actor");
		Roster roster = rosters.findByMonth(month)
				.orElseThrow(() -> new IllegalArgumentException("no roster for " + month));
		if (roster.status() == RosterStatus.PUBLISHED) {
			throw new IllegalStateException(month + " is already published");
		}
		Roster published = roster.withStatus(RosterStatus.PUBLISHED);
		rosters.save(published);
		audit.append(new AuditEvent(clock.instant(), actor.username(), month, "published " + month));
		return published;
	}

	/**
	 * Takes {@code leave}'s doctor off every shift that starts on one of the leave days, and
	 * returns the assignments removed. Writes one audit event per freed shift.
	 *
	 * <p>Unlike {@link #applyEdit}, this may leave new HARD violations behind, typically a
	 * shift below minimum staffing: approved leave wins over staffing, and the freed shifts are
	 * for the admin to fill or swap. The leave must fall inside one month, as a leave request does.
	 *
	 * @throws IllegalArgumentException if the leave's month has no roster
	 */
	@Transactional
	public List<Assignment> releaseForLeave(Leave leave, Actor actor) {
		Objects.requireNonNull(leave, "leave");
		Objects.requireNonNull(actor, "actor");
		YearMonth month = YearMonth.from(leave.firstDay());
		Roster roster = rosters.findByMonth(month)
				.orElseThrow(() -> new IllegalArgumentException("no roster for " + month));
		List<Assignment> freed = roster.assignments().stream()
				.filter(assignment -> assignment.staff().id().equals(leave.staff().id()))
				.filter(assignment -> leave.covers(assignment.slot().date()))
				.toList();
		if (freed.isEmpty()) {
			return freed;
		}
		Roster after = roster;
		for (Assignment assignment : freed) {
			after = after.withoutAssignment(assignment);
		}
		rosters.save(after);
		for (Assignment assignment : freed) {
			audit.append(new AuditEvent(clock.instant(), actor.username(), month,
					"freed " + describe(assignment) + " from " + leave.staff().id() + ": approved leave"));
		}
		return freed;
	}

	/** Writes the audit event for approved leave being cancelled. */
	@Transactional
	public void recordLeaveCancelled(Leave leave, Actor actor) {
		Objects.requireNonNull(leave, "leave");
		Objects.requireNonNull(actor, "actor");
		audit.append(new AuditEvent(clock.instant(), actor.username(), YearMonth.from(leave.firstDay()),
				"cancelled leave for " + leave.staff().id() + " from " + leave.firstDay() + " to " + leave.lastDay()));
	}

	/** The roster with removals applied, and an assignment to add (if any). */
	private record Change(Roster without, Optional<Assignment> added, String summary) {
	}

	private Change plan(EditCommand command, Roster before) {
		return switch (command) {
			case EditCommand.Assign assign -> {
				Assignment added = new Assignment(assign.slot(), staffById(assign.staffId()), assign.post());
				yield new Change(before, Optional.of(added), "assigned " + assign.staffId() + " to " + describe(added));
			}
			case EditCommand.Unassign unassign -> {
				Assignment removed = existing(before, unassign.slot(), staffById(unassign.staffId()));
				yield new Change(before.withoutAssignment(removed), Optional.empty(),
						"removed " + unassign.staffId() + " from " + describe(removed));
			}
			case EditCommand.Move move -> {
				Assignment removed = existing(before, move.slot(), staffById(move.fromStaffId()));
				Assignment added = new Assignment(move.slot(), staffById(move.toStaffId()), removed.post());
				yield new Change(before.withoutAssignment(removed), Optional.of(added),
						"moved " + describe(removed) + " from " + move.fromStaffId() + " to " + move.toStaffId());
			}
			case EditCommand.MoveWeeklyOff moveOff -> {
				Staff doctor = staffById(moveOff.staffId());
				DayOff current = new DayOff(doctor, moveOff.fromDate());
				if (!before.hasWeeklyOff(doctor, moveOff.fromDate())) {
					throw new IllegalArgumentException(moveOff.staffId() + " has no weekly off on " + moveOff.fromDate());
				}
				if (before.hasWeeklyOff(doctor, moveOff.toDate())) {
					throw new IllegalArgumentException(moveOff.staffId() + " already has a weekly off on " + moveOff.toDate());
				}
				Roster next = before.withoutWeeklyOff(current).withWeeklyOffs(List.of(new DayOff(doctor, moveOff.toDate())));
				yield new Change(next, Optional.empty(),
						"moved weekly off for " + moveOff.staffId() + " from " + moveOff.fromDate() + " to " + moveOff.toDate());
			}
		};
	}

	private Staff staffById(String id) {
		return staff.findById(id).orElseThrow(() -> new IllegalArgumentException("unknown staff: " + id));
	}

	/** {@code member}'s assignment on {@code slot}, in whatever post. */
	private static Assignment existing(Roster roster, ShiftSlot slot, Staff member) {
		return roster.assignmentsOn(slot).stream()
				.filter(a -> a.staff().equals(member))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(member.id() + " is not on " + slot));
	}

	/** E.g. "I (WARD) on 2026-10-05". */
	private static String describe(Assignment assignment) {
		return assignment.slot().shift().code() + " (" + assignment.post() + ") on " + assignment.slot().date();
	}

	private static Violation doubleBooking(Assignment assignment) {
		return new Violation(DOUBLE_BOOKING, Severity.HARD, assignment.slot().date(),
				assignment.staff().name() + " already has a shift overlapping " + assignment.slot().shift().code());
	}

	private static List<Violation> check(List<Constraint> active, Roster roster) {
		List<Violation> all = new ArrayList<>();
		for (Constraint constraint : active) {
			all.addAll(constraint.check(roster));
		}
		return all;
	}

	/**
	 * Violations of {@code severity} that are in {@code after} but not {@code before}
	 * (matching one-for-one, so duplicates count), for constraints and dates whose count went up.
	 */
	private static List<Violation> added(List<Violation> before, List<Violation> after, Severity severity) {
		Map<Key, Integer> countBefore = countByKey(before, severity);
		Map<Key, Integer> countAfter = countByKey(after, severity);
		List<Violation> unmatched = new ArrayList<>(after.stream().filter(v -> v.severity() == severity).toList());
		for (Violation old : before) {
			unmatched.remove(old);
		}
		return unmatched.stream()
				.filter(v -> countAfter.get(Key.of(v)) > countBefore.getOrDefault(Key.of(v), 0))
				.toList();
	}

	private static Map<Key, Integer> countByKey(List<Violation> violations, Severity severity) {
		Map<Key, Integer> counts = new HashMap<>();
		for (Violation violation : violations) {
			if (violation.severity() == severity) {
				counts.merge(Key.of(violation), 1, Integer::sum);
			}
		}
		return counts;
	}

	private record Key(String constraintId, LocalDate date) {

		static Key of(Violation violation) {
			return new Key(violation.constraintId(), violation.date());
		}

	}

}
