package com.iitm.roster.constraints;

import com.iitm.roster.domain.Assignment;
import com.iitm.roster.domain.Roster;
import com.iitm.roster.domain.ShiftType;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * After a night, the doctor has the next day off (post-duty off): no shift may start on it.
 * Nights carried in from the month before count too, so the doctor on the night of its last day
 * is off on this month's 1st.
 */
public final class PostDutyOffConstraint implements Constraint {

	public static final String ID = "post-duty-off";

	private final ShiftType night;
	private final Severity severity;
	private final List<Assignment> carriedIn;

	public PostDutyOffConstraint(ShiftType night, Severity severity) {
		this(night, severity, List.of());
	}

	/** @param carriedIn assignments from the month before; only its nights matter here */
	public PostDutyOffConstraint(ShiftType night, Severity severity, List<Assignment> carriedIn) {
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
		List<Assignment> nightsAndCarriedIn = new ArrayList<>(carriedIn);
		nightsAndCarriedIn.addAll(roster.assignments());
		for (Assignment nightDuty : nightsAndCarriedIn) {
			if (!nightDuty.slot().shift().equals(night)) {
				continue;
			}
			LocalDate offDay = nightDuty.slot().date().plusDays(1);
			for (Assignment other : index.of(nightDuty.staff())) {
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
