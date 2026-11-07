package com.iitm.roster.domain;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The roster for one month: which staff work which slots, and who has which weekly off.
 * Immutable; the {@code with...} methods return a new roster.
 *
 * <p>Every slot and weekly off is inside {@code month} (a night shift on the last day may
 * end in the next month), and nobody is assigned two slots that overlap in time. Whether a
 * weekly off clashes with a shift is checked by a constraint, not here.
 */
public record Roster(YearMonth month, RosterStatus status, List<Assignment> assignments, List<DayOff> weeklyOffs) {

	public Roster {
		Objects.requireNonNull(month, "month");
		Objects.requireNonNull(status, "status");
		assignments = List.copyOf(assignments);
		weeklyOffs = List.copyOf(weeklyOffs);
		for (Assignment assignment : assignments) {
			if (!YearMonth.from(assignment.slot().date()).equals(month)) {
				throw new IllegalArgumentException("slot " + assignment.slot().date() + " is outside " + month);
			}
		}
		for (DayOff off : weeklyOffs) {
			if (!YearMonth.from(off.date()).equals(month)) {
				throw new IllegalArgumentException("weekly off " + off.date() + " is outside " + month);
			}
		}
		rejectDoubleBooking(assignments);
	}

	/** A roster with no weekly offs recorded. */
	public Roster(YearMonth month, RosterStatus status, List<Assignment> assignments) {
		this(month, status, assignments, List.of());
	}

	/** An empty draft roster for {@code month}. */
	public static Roster empty(YearMonth month) {
		return new Roster(month, RosterStatus.DRAFT, List.of());
	}

	/** Every date of the month, in order. */
	public List<LocalDate> days() {
		return month.atDay(1).datesUntil(month.atEndOfMonth().plusDays(1)).toList();
	}

	public List<Assignment> assignmentsFor(Staff staff) {
		return assignments.stream().filter(a -> a.staff().equals(staff)).toList();
	}

	public List<Assignment> assignmentsOn(ShiftSlot slot) {
		return assignments.stream().filter(a -> a.slot().equals(slot)).toList();
	}

	/** True if adding {@code assignment} would put its staff member in two overlapping slots. */
	public boolean wouldDoubleBook(Assignment assignment) {
		return assignments.stream().anyMatch(a -> a.staff().equals(assignment.staff())
				&& a.slot().overlaps(assignment.slot()));
	}

	/** A copy with {@code assignment} added; throws if it double-books someone. */
	public Roster withAssignment(Assignment assignment) {
		List<Assignment> changed = new ArrayList<>(assignments);
		changed.add(assignment);
		return new Roster(month, status, changed, weeklyOffs);
	}

	/** A copy without {@code assignment}; throws if it is not in this roster. */
	public Roster withoutAssignment(Assignment assignment) {
		List<Assignment> changed = new ArrayList<>(assignments);
		if (!changed.remove(assignment)) {
			throw new IllegalArgumentException("not in roster: " + assignment);
		}
		return new Roster(month, status, changed, weeklyOffs);
	}

	public Roster withStatus(RosterStatus newStatus) {
		return new Roster(month, newStatus, assignments, weeklyOffs);
	}

	/** A copy with {@code offs} added to the weekly offs. */
	public Roster withWeeklyOffs(List<DayOff> offs) {
		List<DayOff> changed = new ArrayList<>(weeklyOffs);
		changed.addAll(offs);
		return new Roster(month, status, assignments, changed);
	}

	/** True if {@code staff} has a weekly off on {@code date}. */
	public boolean hasWeeklyOff(Staff staff, LocalDate date) {
		return weeklyOffs.contains(new DayOff(staff, date));
	}

	/**
	 * Throws on the first pair (lowest first index, then lowest second) of one person's
	 * assignments that overlap. Every roster change builds a new roster and so runs this, so the
	 * usual case (no overlap) is one pass per person over their shifts sorted by start time; only
	 * when an overlap exists are the pairs searched to name the same pair as always.
	 */
	private static void rejectDoubleBooking(List<Assignment> assignments) {
		Map<Staff, List<Integer>> byStaff = new HashMap<>();
		for (int i = 0; i < assignments.size(); i++) {
			byStaff.computeIfAbsent(assignments.get(i).staff(), staff -> new ArrayList<>()).add(i);
		}
		for (List<Integer> indices : byStaff.values()) {
			if (anyOverlap(assignments, indices)) {
				throw firstDoubleBooking(assignments);
			}
		}
	}

	/**
	 * True if any two of these assignments overlap. Sorted by start, it is enough to compare each
	 * start with the previous end: if no overlap has been found yet, the previous slot ends after
	 * every earlier one.
	 */
	private static boolean anyOverlap(List<Assignment> assignments, List<Integer> indices) {
		List<ShiftSlot> slots = new ArrayList<>();
		for (int i : indices) {
			slots.add(assignments.get(i).slot());
		}
		slots.sort(Comparator.comparing(ShiftSlot::start));
		ZonedDateTime previousEnd = null;
		for (ShiftSlot slot : slots) {
			if (previousEnd != null && slot.start().isBefore(previousEnd)) {
				return true;
			}
			previousEnd = slot.end();
		}
		return false;
	}

	private static IllegalArgumentException firstDoubleBooking(List<Assignment> assignments) {
		for (int i = 0; i < assignments.size(); i++) {
			for (int j = i + 1; j < assignments.size(); j++) {
				Assignment a = assignments.get(i);
				Assignment b = assignments.get(j);
				if (a.staff().equals(b.staff()) && a.slot().overlaps(b.slot())) {
					return new IllegalArgumentException("double-booked: " + a + " and " + b);
				}
			}
		}
		throw new IllegalStateException("an overlap was found but no overlapping pair");
	}

}
