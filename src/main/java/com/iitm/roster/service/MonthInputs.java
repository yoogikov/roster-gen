package com.iitm.roster.service;

import com.iitm.roster.repo.StoredMonthInput;
import com.iitm.roster.scheduler.MonthInput;

/** Converts between the scheduler's {@link MonthInput} and its stored form. */
final class MonthInputs {

	private MonthInputs() {
	}

	static StoredMonthInput toStored(MonthInput input) {
		return new StoredMonthInput(input.month(), input.doctors(), input.firstOnNights(), input.leaves(),
				input.calendar(), input.weekdayPerShift(), input.weekendPerShift());
	}

	static MonthInput fromStored(StoredMonthInput stored) {
		return new MonthInput(stored.month(), stored.doctors(), stored.firstOnNights(), stored.leaves(),
				stored.calendar(), stored.weekdayPerShift(), stored.weekendPerShift());
	}

}
