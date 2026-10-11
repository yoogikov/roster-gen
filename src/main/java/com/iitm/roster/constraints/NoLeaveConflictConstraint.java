package com.iitm.roster.constraints;

import com.iitm.roster.domain.Assignment;
import com.iitm.roster.domain.Leave;
import com.iitm.roster.domain.Roster;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Nobody works a shift that starts on a day they are on approved leave. A night shift that
 * starts the evening before leave and ends on its first morning is allowed (to confirm
 * with the CMO).
 */
public final class NoLeaveConflictConstraint implements Constraint {

	public static final String ID = "no-leave-conflict";

	private final List<Leave> leaves;
	private final Severity severity;

	public NoLeaveConflictConstraint(List<Leave> leaves, Severity severity) {
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
		for (Assignment assignment : roster.assignments()) {
			LocalDate day = assignment.slot().date();
			for (Leave leave : leaves) {
				if (leave.staff().equals(assignment.staff()) && leave.covers(day)) {
					violations.add(new Violation(ID, severity, day,
							String.format("%s is on leave but rostered for %s",
									assignment.staff().name(), assignment.slot().shift().code())));
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
