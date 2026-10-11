package com.iitm.roster.scheduler;

import com.iitm.roster.domain.ShiftSlot;
import com.iitm.roster.domain.StaffRole;
import java.util.Objects;

/** One person of {@code role} still needed on {@code slot}. */
public record OpenSlot(ShiftSlot slot, StaffRole role) {

	public OpenSlot {
		Objects.requireNonNull(slot, "slot");
		Objects.requireNonNull(role, "role");
	}

}
