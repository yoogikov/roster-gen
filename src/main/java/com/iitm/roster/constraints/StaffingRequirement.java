package com.iitm.roster.constraints;

import com.iitm.roster.domain.ShiftType;
import com.iitm.roster.domain.StaffRole;
import java.util.Objects;

/** At least {@code minimum} people of {@code role} on every {@code shift} of the month. */
public record StaffingRequirement(ShiftType shift, StaffRole role, int minimum) {

	public StaffingRequirement {
		Objects.requireNonNull(shift, "shift");
		Objects.requireNonNull(role, "role");
		if (minimum < 0) {
			throw new IllegalArgumentException("minimum must not be negative: " + minimum);
		}
	}

}
