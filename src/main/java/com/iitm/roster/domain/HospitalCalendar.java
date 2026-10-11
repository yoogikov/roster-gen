package com.iitm.roster.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Set;

/** Which days are weekends or hospital holidays, and how days group into weeks. */
public record HospitalCalendar(Set<LocalDate> holidays) {

	public HospitalCalendar {
		holidays = Set.copyOf(holidays);
	}

	public static HospitalCalendar noHolidays() {
		return new HospitalCalendar(Set.of());
	}

	public boolean isWeekendOrHoliday(LocalDate date) {
		DayOfWeek day = date.getDayOfWeek();
		return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY || holidays.contains(date);
	}

	/** The Monday that starts {@code date}'s week. Weeks run Monday to Sunday. */
	public static LocalDate weekStart(LocalDate date) {
		return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
	}

}
