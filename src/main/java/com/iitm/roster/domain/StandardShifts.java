package com.iitm.roster.domain;

import java.time.Duration;
import java.time.LocalTime;
import java.util.List;

/**
 * IITM Hospital's doctor shifts, as on the CMO's roster sheet. The night doctor also works
 * Shift I that day as the ward doctor; that pairing is a rule, not part of these times.
 */
public final class StandardShifts {

	public static final ShiftType SHIFT_I = new ShiftType("I", LocalTime.of(8, 0), Duration.ofMinutes(330));
	public static final ShiftType SHIFT_II = new ShiftType("II", LocalTime.of(13, 0), Duration.ofMinutes(330));
	public static final ShiftType NIGHT = new ShiftType("N", LocalTime.of(18, 30), Duration.ofMinutes(810));

	/** The two day shifts, morning first. */
	public static final List<ShiftType> DAY_SHIFTS = List.of(SHIFT_I, SHIFT_II);

	private StandardShifts() {
	}

}
