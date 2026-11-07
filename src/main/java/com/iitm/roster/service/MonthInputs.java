package com.iitm.roster.service;

import com.iitm.roster.domain.Assignment;
import com.iitm.roster.domain.ShiftType;
import com.iitm.roster.repo.RosterStore;
import com.iitm.roster.repo.StoredMonthInput;
import com.iitm.roster.scheduler.MonthInput;
import java.time.LocalDate;
import java.util.List;

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

	/**
	 * {@code input} with the nights of the previous month's last day carried in, read from that
	 * month's stored roster (none if it has no roster). Those nights are the only assignments the
	 * hospital rules look back at: their doctors are post-duty off on the 1st.
	 */
	static MonthInput withCarriedIn(MonthInput input, RosterStore rosters, ShiftType night) {
		LocalDate dayBefore = input.month().atDay(1).minusDays(1);
		List<Assignment> lastNights = rosters.findByMonth(input.month().minusMonths(1))
				.map(previous -> previous.assignments().stream()
						.filter(a -> a.slot().shift().equals(night) && a.slot().date().equals(dayBefore))
						.toList())
				.orElse(List.of());
		return input.withCarriedIn(lastNights);
	}

}
