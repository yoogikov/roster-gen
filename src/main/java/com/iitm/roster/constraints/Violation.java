package com.iitm.roster.constraints;

import java.time.LocalDate;
import java.util.Objects;

/**
 * One way a roster breaks one constraint.
 *
 * @param constraintId the {@link Constraint#id()} that was broken
 * @param date the day the problem is on, so the editor can point at it
 * @param message what is wrong, for the CMO to read
 */
public record Violation(String constraintId, Severity severity, LocalDate date, String message) {

	public Violation {
		Objects.requireNonNull(constraintId, "constraintId");
		Objects.requireNonNull(severity, "severity");
		Objects.requireNonNull(date, "date");
		Objects.requireNonNull(message, "message");
	}

}
