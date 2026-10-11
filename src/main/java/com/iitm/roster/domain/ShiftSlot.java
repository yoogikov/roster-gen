package com.iitm.roster.domain;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * One shift on one date, e.g. the night shift of 31 Dec 2026. The date is the day the
 * shift starts; a night shift ends on the next day.
 */
public record ShiftSlot(LocalDate date, ShiftType shift) {

	public ShiftSlot {
		Objects.requireNonNull(date, "date");
		Objects.requireNonNull(shift, "shift");
	}

	public ZonedDateTime start() {
		return date.atTime(shift.start()).atZone(HospitalTime.ZONE);
	}

	public ZonedDateTime end() {
		return start().plus(shift.length());
	}

	/** True if the two slots share any time. A slot ending exactly when another starts does not overlap it. */
	public boolean overlaps(ShiftSlot other) {
		return start().isBefore(other.end()) && other.start().isBefore(end());
	}

}
