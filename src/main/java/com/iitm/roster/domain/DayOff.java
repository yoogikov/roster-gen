package com.iitm.roster.domain;

import java.time.LocalDate;
import java.util.Objects;

/** A staff member's weekly off. */
public record DayOff(Staff staff, LocalDate date) {

	public DayOff {
		Objects.requireNonNull(staff, "staff");
		Objects.requireNonNull(date, "date");
	}

}
