package com.iitm.roster.constraints;

import com.iitm.roster.domain.Assignment;
import com.iitm.roster.domain.DayOff;
import com.iitm.roster.domain.HospitalCalendar;
import com.iitm.roster.domain.Roster;
import com.iitm.roster.domain.ShiftType;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Weekly offs, as recorded in the roster:
 * <ul>
 *   <li>nobody works on their own weekly off;
 *   <li>a weekly off is not the post-duty-off day after a night (they may be back to back),
 *       including a night carried in from the last day of the month before.
 * </ul>
 *
 * <p>Going without an off is not a violation. It is an off the doctor is owed, and the next month
 * gives it (see {@code OwedOffs}). {@link #countedWeeks} says which weeks entitle a doctor to one:
 * those with at least {@value #MIN_DAYS_IN_MONTH} days in this month. A shorter week at the start
 * or end belongs to the neighbouring month.
 */
public final class WeeklyOffConstraint implements Constraint {

	public static final String ID = "weekly-off";
	public static final int MIN_DAYS_IN_MONTH = 4;

	private final ShiftType night;
	private final Severity severity;
	private final List<Assignment> carriedIn;

	public WeeklyOffConstraint(ShiftType night, Severity severity) {
		this(night, severity, List.of());
	}

	/** @param carriedIn assignments from the month before; only its nights matter here */
	public WeeklyOffConstraint(ShiftType night, Severity severity, List<Assignment> carriedIn) {
		this.night = Objects.requireNonNull(night, "night");
		this.severity = Objects.requireNonNull(severity, "severity");
		this.carriedIn = List.copyOf(carriedIn);
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public Severity severity() {
		return severity;
	}

	@Override
	public List<Violation> check(Roster roster) {
		List<Violation> violations = new ArrayList<>();
		RosterIndex index = new RosterIndex(roster);
		for (DayOff off : roster.weeklyOffs()) {
			List<Assignment> duties = new ArrayList<>(index.of(off.staff()));
			carriedIn.stream().filter(a -> a.staff().equals(off.staff())).forEach(duties::add);
			for (Assignment assignment : duties) {
				if (assignment.slot().date().equals(off.date())) {
					violations.add(new Violation(ID, severity, off.date(),
							off.staff().name() + " is rostered on their weekly off"));
				}
				if (assignment.slot().shift().equals(night) && assignment.slot().date().plusDays(1).equals(off.date())) {
					violations.add(new Violation(ID, severity, off.date(),
							off.staff().name() + "'s weekly off falls on their post-duty off"));
				}
			}
		}
		return violations;
	}

	/** The in-month days of each week that counts, in order. */
	public static List<List<LocalDate>> countedWeeks(Roster roster) {
		List<List<LocalDate>> weeks = new ArrayList<>();
		List<LocalDate> current = new ArrayList<>();
		for (LocalDate day : roster.days()) {
			if (!current.isEmpty() && !HospitalCalendar.weekStart(day).equals(HospitalCalendar.weekStart(current.get(0)))) {
				addIfCounted(weeks, current);
				current = new ArrayList<>();
			}
			current.add(day);
		}
		addIfCounted(weeks, current);
		return weeks;
	}

	private static void addIfCounted(List<List<LocalDate>> weeks, List<LocalDate> week) {
		if (week.size() >= MIN_DAYS_IN_MONTH) {
			weeks.add(List.copyOf(week));
		}
	}

	@Override
	public int penalty(Roster roster) {
		return Penalties.perViolation(severity, check(roster));
	}

}
