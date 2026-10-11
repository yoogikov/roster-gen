package com.iitm.roster.constraints;

import com.iitm.roster.domain.Assignment;
import com.iitm.roster.domain.Roster;
import com.iitm.roster.domain.ShiftSlot;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Every shift on every day of the month has at least the required number of doctors and
 * nurses. Per-date overrides (Saarang, holidays) come later from {@code rules}.
 */
public final class MinStaffingConstraint implements Constraint {

	public static final String ID = "min-staffing";

	private final List<StaffingRequirement> requirements;
	private final Severity severity;

	public MinStaffingConstraint(List<StaffingRequirement> requirements, Severity severity) {
		this.requirements = List.copyOf(requirements);
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
		for (LocalDate day : roster.days()) {
			for (StaffingRequirement requirement : requirements) {
				ShiftSlot slot = new ShiftSlot(day, requirement.shift());
				long present = roster.assignmentsOn(slot).stream()
						.map(Assignment::staff)
						.filter(staff -> staff.role() == requirement.role())
						.count();
				if (present < requirement.minimum()) {
					violations.add(new Violation(ID, severity, day,
							String.format("%s shift needs %d %s, has %d",
									requirement.shift().code(), requirement.minimum(), requirement.role(), present)));
				}
			}
		}
		return violations;
	}

	@Override
	public int penalty(Roster roster) {
		return Penalties.perViolation(severity, check(roster));
	}

}
