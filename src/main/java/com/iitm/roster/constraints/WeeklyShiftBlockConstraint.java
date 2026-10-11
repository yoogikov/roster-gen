package com.iitm.roster.constraints;

import com.iitm.roster.domain.Assignment;
import com.iitm.roster.domain.HospitalCalendar;
import com.iitm.roster.domain.Roster;
import com.iitm.roster.domain.ShiftType;
import com.iitm.roster.domain.Staff;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Each week a doctor works mornings or afternoons, not a mix, and alternates week to week.
 * Usually SOFT: the night doctor always works that morning, so a doctor on an afternoons
 * week breaks it on their night (the CMO's sheet does the same).
 *
 * <p>Per doctor and week, one violation for every shift on the less-used block, plus one if
 * the week uses the same block as their previous week.
 */
public final class WeeklyShiftBlockConstraint implements Constraint {

	public static final String ID = "weekly-shift-block";

	private final ShiftType morning;
	private final ShiftType afternoon;
	private final Severity severity;

	public WeeklyShiftBlockConstraint(ShiftType morning, ShiftType afternoon, Severity severity) {
		this.morning = Objects.requireNonNull(morning, "morning");
		this.afternoon = Objects.requireNonNull(afternoon, "afternoon");
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
		Map<Staff, SortedMap<LocalDate, int[]>> counts = new LinkedHashMap<>();
		for (Assignment assignment : roster.assignments()) {
			ShiftType shift = assignment.slot().shift();
			if (!shift.equals(morning) && !shift.equals(afternoon)) {
				continue;
			}
			LocalDate week = HospitalCalendar.weekStart(assignment.slot().date());
			int[] perBlock = counts.computeIfAbsent(assignment.staff(), s -> new TreeMap<>())
					.computeIfAbsent(week, w -> new int[2]);
			perBlock[shift.equals(morning) ? 0 : 1]++;
		}
		List<Violation> violations = new ArrayList<>();
		counts.forEach((staff, weeks) -> {
			Boolean previousMornings = null;
			for (Map.Entry<LocalDate, int[]> week : weeks.entrySet()) {
				int mornings = week.getValue()[0];
				int afternoons = week.getValue()[1];
				for (int i = 0; i < Math.min(mornings, afternoons); i++) {
					violations.add(new Violation(ID, severity, week.getKey(),
							staff.name() + " mixes mornings and afternoons in one week (" + (i + 1) + ")"));
				}
				boolean mostlyMornings = mornings >= afternoons;
				if (previousMornings != null && previousMornings == mostlyMornings) {
					violations.add(new Violation(ID, severity, week.getKey(),
							staff.name() + " does not alternate mornings and afternoons from the week before"));
				}
				previousMornings = mostlyMornings;
			}
		});
		return violations;
	}

	@Override
	public int penalty(Roster roster) {
		return Penalties.perViolation(severity, check(roster));
	}

}
