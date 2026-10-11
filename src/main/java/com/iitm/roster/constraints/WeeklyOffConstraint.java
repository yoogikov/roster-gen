package com.iitm.roster.constraints;

import com.iitm.roster.domain.Assignment;
import com.iitm.roster.domain.DayOff;
import com.iitm.roster.domain.HospitalCalendar;
import com.iitm.roster.domain.Leave;
import com.iitm.roster.domain.Roster;
import com.iitm.roster.domain.ShiftType;
import com.iitm.roster.domain.Staff;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Weekly offs, as recorded in the roster:
 * <ul>
 *   <li>nobody works on their own weekly off;
 *   <li>a weekly off is not the post-duty-off day after a night (they may be back to back);
 *   <li>everyone on the roster has one in every week that has at least
 *       {@value #MIN_DAYS_IN_MONTH} days in this month, unless they are on leave for all of
 *       those days. A shorter week at the start or end belongs to the neighbouring month.
 * </ul>
 */
public final class WeeklyOffConstraint implements Constraint {

	public static final String ID = "weekly-off";
	public static final int MIN_DAYS_IN_MONTH = 4;

	private final ShiftType night;
	private final List<Leave> leaves;
	private final Severity severity;

	public WeeklyOffConstraint(ShiftType night, List<Leave> leaves, Severity severity) {
		this.night = Objects.requireNonNull(night, "night");
		this.leaves = List.copyOf(leaves);
		this.severity = Objects.requireNonNull(severity, "severity");
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
		for (DayOff off : roster.weeklyOffs()) {
			for (Assignment assignment : roster.assignmentsFor(off.staff())) {
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
		for (List<LocalDate> week : countedWeeks(roster)) {
			for (Staff staff : staffOn(roster)) {
				boolean hasOff = week.stream().anyMatch(day -> roster.hasWeeklyOff(staff, day));
				if (!hasOff && !onLeaveAllOf(staff, week)) {
					violations.add(new Violation(ID, severity, week.get(0),
							staff.name() + " has no weekly off in the week of " + HospitalCalendar.weekStart(week.get(0))));
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

	private static Set<Staff> staffOn(Roster roster) {
		Set<Staff> staff = new LinkedHashSet<>();
		roster.assignments().forEach(a -> staff.add(a.staff()));
		return staff;
	}

	private boolean onLeaveAllOf(Staff staff, List<LocalDate> days) {
		return days.stream().allMatch(day -> leaves.stream().anyMatch(l -> l.staff().equals(staff) && l.covers(day)));
	}

	@Override
	public int penalty(Roster roster) {
		return Penalties.perViolation(severity, check(roster));
	}

}
