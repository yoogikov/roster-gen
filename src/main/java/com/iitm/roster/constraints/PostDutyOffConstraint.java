package com.iitm.roster.constraints;

import com.iitm.roster.domain.Assignment;
import com.iitm.roster.domain.Roster;
import com.iitm.roster.domain.ShiftType;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** After a night, the doctor has the next day off (post-duty off): no shift may start on it. */
public final class PostDutyOffConstraint implements Constraint {

	public static final String ID = "post-duty-off";

	private final ShiftType night;
	private final Severity severity;

	public PostDutyOffConstraint(ShiftType night, Severity severity) {
		this.night = Objects.requireNonNull(night, "night");
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
		for (Assignment nightDuty : roster.assignments()) {
			if (!nightDuty.slot().shift().equals(night)) {
				continue;
			}
			LocalDate offDay = nightDuty.slot().date().plusDays(1);
			for (Assignment other : roster.assignmentsFor(nightDuty.staff())) {
				if (other.slot().date().equals(offDay)) {
					violations.add(new Violation(ID, severity, offDay, String.format("%s is post-duty off but rostered for %s",
							other.staff().name(), other.slot().shift().code())));
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
