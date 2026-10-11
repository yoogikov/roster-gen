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
 * nurses, as set by a {@link StaffingPlan} (which can differ on weekends and holidays).
 *
 * <p>The message deliberately leaves out how many people are there now: the generator
 * treats a violation that stays the same while a shift fills up as "not new", and a count
 * in the message would change with every person added.
 */
public final class MinStaffingConstraint implements Constraint {

	public static final String ID = "min-staffing";

	private final StaffingPlan plan;
	private final Severity severity;

	public MinStaffingConstraint(StaffingPlan plan, Severity severity) {
		this.plan = Objects.requireNonNull(plan, "plan");
		this.severity = Objects.requireNonNull(severity, "severity");
	}

	/** The same requirements every day. */
	public MinStaffingConstraint(List<StaffingRequirement> requirements, Severity severity) {
		this(StaffingPlan.constant(requirements), severity);
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
			for (StaffingRequirement requirement : plan.on(day)) {
				ShiftSlot slot = new ShiftSlot(day, requirement.shift());
				long present = roster.assignmentsOn(slot).stream()
						.map(Assignment::staff)
						.filter(staff -> staff.role() == requirement.role())
						.count();
				if (present < requirement.minimum()) {
					violations.add(new Violation(ID, severity, day,
							String.format("%s shift needs at least %d %s",
									requirement.shift().code(), requirement.minimum(), requirement.role())));
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
