package com.iitm.roster.scheduler;

import com.iitm.roster.domain.HospitalCalendar;
import com.iitm.roster.domain.Leave;
import com.iitm.roster.domain.Staff;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;

/**
 * Everything needed to roster one month of doctors.
 *
 * @param doctors all doctors, in night-rotation order
 * @param firstOnNights who does the first night of the month (continuing last month's order)
 * @param weekdayPerShift minimum doctors on each day shift on a weekday
 * @param weekendPerShift minimum doctors on each day shift on a weekend or holiday
 */
public record MonthInput(YearMonth month, List<Staff> doctors, Staff firstOnNights, List<Leave> leaves,
		HospitalCalendar calendar, int weekdayPerShift, int weekendPerShift) {

	public MonthInput {
		Objects.requireNonNull(month, "month");
		Objects.requireNonNull(firstOnNights, "firstOnNights");
		Objects.requireNonNull(calendar, "calendar");
		doctors = List.copyOf(doctors);
		leaves = List.copyOf(leaves);
		if (weekdayPerShift < 2 || weekendPerShift < 2) {
			throw new IllegalArgumentException("each day shift needs at least a ward and a casualty doctor");
		}
	}

}
