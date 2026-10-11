package com.iitm.roster.domain;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The roster for one month: which staff work which slots. Immutable; the {@code with...}
 * methods return a new roster.
 *
 * <p>Every slot starts inside {@code month} (a night shift on the last day may end in the
 * next month), and nobody is assigned two slots that overlap in time.
 */
public record Roster(YearMonth month, RosterStatus status, List<Assignment> assignments) {

	public Roster {
		Objects.requireNonNull(month, "month");
		Objects.requireNonNull(status, "status");
		assignments = List.copyOf(assignments);
		for (Assignment assignment : assignments) {
			if (!YearMonth.from(assignment.slot().date()).equals(month)) {
				throw new IllegalArgumentException("slot " + assignment.slot().date() + " is outside " + month);
			}
		}
		rejectDoubleBooking(assignments);
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
		return new Roster(month, status, changed);
	}

	/** A copy without {@code assignment}; throws if it is not in this roster. */
	public Roster withoutAssignment(Assignment assignment) {
		List<Assignment> changed = new ArrayList<>(assignments);
		if (!changed.remove(assignment)) {
			throw new IllegalArgumentException("not in roster: " + assignment);
		}
		return new Roster(month, status, changed);
	}

	public Roster withStatus(RosterStatus newStatus) {
		return new Roster(month, newStatus, assignments);
	}

	private static void rejectDoubleBooking(List<Assignment> assignments) {
		for (int i = 0; i < assignments.size(); i++) {
			for (int j = i + 1; j < assignments.size(); j++) {
				Assignment a = assignments.get(i);
				Assignment b = assignments.get(j);
				if (a.staff().equals(b.staff()) && a.slot().overlaps(b.slot())) {
					throw new IllegalArgumentException("double-booked: " + a + " and " + b);
				}
			}
		}
	}

}
