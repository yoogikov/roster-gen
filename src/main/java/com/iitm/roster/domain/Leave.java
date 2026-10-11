package com.iitm.roster.domain;

import java.time.LocalDate;
import java.util.Objects;

/** Approved leave for one staff member, from {@code firstDay} to {@code lastDay} inclusive. */
public record Leave(Staff staff, LocalDate firstDay, LocalDate lastDay) {

	public Leave {
		Objects.requireNonNull(staff, "staff");
		Objects.requireNonNull(firstDay, "firstDay");
		Objects.requireNonNull(lastDay, "lastDay");
		if (lastDay.isBefore(firstDay)) {
			throw new IllegalArgumentException("leave ends before it starts: " + firstDay + " to " + lastDay);
		}
	}

	public boolean covers(LocalDate date) {
		return !date.isBefore(firstDay) && !date.isAfter(lastDay);
	}

}
