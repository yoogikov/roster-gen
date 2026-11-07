package com.iitm.roster.scheduler;

import com.iitm.roster.domain.Assignment;
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
 * @param carriedIn assignments from the end of the month before that this month's rules must see,
 *        such as the night of its last day, whose doctor is post-duty off on the 1st. Not stored
 *        with the input: it is read from the previous month's roster each time.
 */
public record MonthInput(YearMonth month, List<Staff> doctors, Staff firstOnNights, List<Leave> leaves,
		HospitalCalendar calendar, int weekdayPerShift, int weekendPerShift, List<Assignment> carriedIn) {

	/** The hospital's minimum doctors on each day shift on a weekday. */
	public static final int WEEKDAY_PER_SHIFT = 4;
	/** The hospital's minimum doctors on each day shift on a weekend or holiday. */
	public static final int WEEKEND_PER_SHIFT = 2;

	public MonthInput {
		Objects.requireNonNull(month, "month");
		Objects.requireNonNull(firstOnNights, "firstOnNights");
		Objects.requireNonNull(calendar, "calendar");
		doctors = List.copyOf(doctors);
		leaves = List.copyOf(leaves);
		// Missing from a JSON request body (clients never send it; the service fills it in), so null means none.
		carriedIn = carriedIn == null ? List.of() : List.copyOf(carriedIn);
		if (weekdayPerShift < 2 || weekendPerShift < 2) {
			throw new IllegalArgumentException("each day shift needs at least a ward and a casualty doctor");
		}
	}

	/** A month with nothing carried in from the month before. */
	public MonthInput(YearMonth month, List<Staff> doctors, Staff firstOnNights, List<Leave> leaves,
			HospitalCalendar calendar, int weekdayPerShift, int weekendPerShift) {
		this(month, doctors, firstOnNights, leaves, calendar, weekdayPerShift, weekendPerShift, List.of());
	}

	/** This input with {@code assignments} carried in from the month before, replacing any already there. */
	public MonthInput withCarriedIn(List<Assignment> assignments) {
		return new MonthInput(month, doctors, firstOnNights, leaves, calendar, weekdayPerShift, weekendPerShift,
				assignments);
	}

	/**
	 * A month under the hospital's usual staffing, with the first doctor in {@code doctors}
	 * doing the first night.
	 */
	public static MonthInput standard(YearMonth month, List<Staff> doctors, List<Leave> leaves, HospitalCalendar calendar) {
		if (doctors.isEmpty()) {
			throw new IllegalArgumentException("no doctors to roster");
		}
		return new MonthInput(month, doctors, doctors.get(0), leaves, calendar, WEEKDAY_PER_SHIFT, WEEKEND_PER_SHIFT);
	}

}
