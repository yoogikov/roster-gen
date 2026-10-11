package com.iitm.roster.service;

import com.iitm.roster.constraints.Constraint;
import com.iitm.roster.constraints.Severity;
import com.iitm.roster.constraints.Violation;
import com.iitm.roster.domain.Assignment;
import com.iitm.roster.domain.Roster;
import com.iitm.roster.domain.ShiftSlot;
import com.iitm.roster.domain.ShiftType;
import com.iitm.roster.domain.Staff;
import java.time.LocalDate;
import java.util.List;

/**
 * Small constraints for testing {@link RosterService} without depending on the real ones
 * in {@code constraints}. Their messages include counts, like the real minimum-staffing
 * constraint, so a violation's message changes as the roster improves.
 */
final class FakeConstraints {

	private FakeConstraints() {
	}

	/** HARD: at least {@code minimum} people on {@code shift} on {@code date}. */
	static Constraint minimumOn(LocalDate date, ShiftType shift, int minimum) {
		return new Fake("min-on", Severity.HARD, roster -> {
			int present = roster.assignmentsOn(new ShiftSlot(date, shift)).size();
			return present >= minimum ? List.of()
					: List.of(new Violation("min-on", Severity.HARD, date, "needs " + minimum + ", has " + present));
		});
	}

	/** HARD: {@code staff} works nothing on {@code date} (like approved leave). */
	static Constraint offOn(Staff staff, LocalDate date) {
		return new Fake("off-on", Severity.HARD, roster -> roster.assignmentsFor(staff).stream()
				.filter(a -> a.slot().date().equals(date))
				.map(a -> new Violation("off-on", Severity.HARD, date, staff.name() + " is off"))
				.toList());
	}

	/** SOFT: one violation per {@code shift} anyone works. */
	static Constraint avoid(ShiftType shift) {
		return new Fake("avoid", Severity.SOFT, roster -> roster.assignments().stream()
				.map(Assignment::slot)
				.filter(slot -> slot.shift().equals(shift))
				.map(slot -> new Violation("avoid", Severity.SOFT, slot.date(), "prefer no " + shift.code()))
				.toList());
	}

	private interface Check {
		List<Violation> apply(Roster roster);
	}

	private record Fake(String id, Severity severity, Check rule) implements Constraint {

		@Override
		public List<Violation> check(Roster roster) {
			return rule.apply(roster);
		}

		@Override
		public int penalty(Roster roster) {
			return severity == Severity.SOFT ? check(roster).size() : 0;
		}

	}

}
