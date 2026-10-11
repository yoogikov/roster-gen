package com.iitm.roster.repo;

import com.iitm.roster.domain.HospitalCalendar;
import com.iitm.roster.domain.Leave;
import com.iitm.roster.domain.Staff;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;

/**
 * The input a month's roster was generated from, as stored. It has the same fields as
 * {@code scheduler.MonthInput}, but {@code repo} may not depend on {@code scheduler} (same
 * layer), so {@code service} converts between the two.
 *
 * @param doctors in night-rotation order
 */
public record StoredMonthInput(YearMonth month, List<Staff> doctors, Staff firstOnNights, List<Leave> leaves,
		HospitalCalendar calendar, int weekdayPerShift, int weekendPerShift) {

	public StoredMonthInput {
		Objects.requireNonNull(month, "month");
		Objects.requireNonNull(firstOnNights, "firstOnNights");
		Objects.requireNonNull(calendar, "calendar");
		doctors = List.copyOf(doctors);
		leaves = List.copyOf(leaves);
	}

}
