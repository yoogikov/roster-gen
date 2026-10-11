package com.iitm.roster.domain;

import java.time.Duration;
import java.time.LocalTime;
import java.util.Objects;

/**
 * A kind of shift, such as a morning or a night shift. A shift may run past midnight
 * (start 20:00, length 12 hours), but never for more than a day.
 *
 * @param code short code used in the roster sheet, e.g. {@code M} or {@code N}
 * @param start local start time in {@link HospitalTime#ZONE}
 * @param length how long the shift lasts
 */
public record ShiftType(String code, LocalTime start, Duration length) {

	public ShiftType {
		if (code == null || code.isBlank()) {
			throw new IllegalArgumentException("code must not be blank");
		}
		Objects.requireNonNull(start, "start");
		Objects.requireNonNull(length, "length");
		if (length.isZero() || length.isNegative() || length.compareTo(Duration.ofDays(1)) > 0) {
			throw new IllegalArgumentException("length must be more than 0 and at most 24 hours: " + length);
		}
	}

}
