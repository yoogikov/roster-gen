package com.iitm.roster.constraints;

import com.iitm.roster.domain.HospitalCalendar;
import java.time.LocalDate;
import java.util.List;

/** How many people each shift needs on a given day. */
@FunctionalInterface
public interface StaffingPlan {

	List<StaffingRequirement> on(LocalDate day);

	/** The same requirements every day. */
	static StaffingPlan constant(List<StaffingRequirement> requirements) {
		List<StaffingRequirement> copy = List.copyOf(requirements);
		return day -> copy;
	}

	/** One set of requirements on weekdays and another on weekends and holidays. */
	static StaffingPlan weekdayAndWeekend(HospitalCalendar calendar, List<StaffingRequirement> weekday,
			List<StaffingRequirement> weekendOrHoliday) {
		List<StaffingRequirement> weekdayCopy = List.copyOf(weekday);
		List<StaffingRequirement> weekendCopy = List.copyOf(weekendOrHoliday);
		return day -> calendar.isWeekendOrHoliday(day) ? weekendCopy : weekdayCopy;
	}

}
