package com.iitm.roster.domain;

import java.util.Objects;

/** One staff member rostered on one slot. */
public record Assignment(ShiftSlot slot, Staff staff) {

	public Assignment {
		Objects.requireNonNull(slot, "slot");
		Objects.requireNonNull(staff, "staff");
	}

}
