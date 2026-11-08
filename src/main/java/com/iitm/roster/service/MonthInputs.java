package com.iitm.roster.service;

import com.iitm.roster.domain.Assignment;
import com.iitm.roster.domain.ShiftType;
import com.iitm.roster.domain.Staff;
import com.iitm.roster.repo.MonthInputStore;
import com.iitm.roster.repo.RosterStore;
import com.iitm.roster.repo.StoredMonthInput;
import com.iitm.roster.scheduler.MonthInput;
import com.iitm.roster.scheduler.OwedOffs;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
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
	 * hospital rules look back at: their doctors are post-duty off on the 1st. Also adds the weekly
	 * offs owed from earlier months, for the doctors still on the list.
	 */
	static MonthInput withCarriedIn(MonthInput input, RosterStore rosters, MonthInputStore inputs, ShiftType night) {
		LocalDate dayBefore = input.month().atDay(1).minusDays(1);
		List<Assignment> lastNights = rosters.findByMonth(input.month().minusMonths(1))
				.map(previous -> previous.assignments().stream()
						.filter(a -> a.slot().shift().equals(night) && a.slot().date().equals(dayBefore))
						.toList())
				.orElse(List.of());
		List<Staff> owed = new ArrayList<>();
		for (Staff doctor : owedAfter(input.month().minusMonths(1), rosters, inputs)) {
			input.doctors().stream().filter(d -> d.id().equals(doctor.id())).findFirst().ifPresent(owed::add);
		}
		return input.withCarriedIn(lastNights).withOwedOffs(owed);
	}

	/**
	 * The weekly offs owed once {@code month} is over, worked through from the earliest stored
	 * month in an unbroken run up to it. Empty if {@code month} has no stored roster and input.
	 */
	static List<Staff> owedAfter(YearMonth month, RosterStore rosters, MonthInputStore inputs) {
		Deque<YearMonth> run = new ArrayDeque<>();
		for (YearMonth at = month; rosters.findByMonth(at).isPresent() && inputs.findByMonth(at).isPresent();
				at = at.minusMonths(1)) {
			run.addFirst(at);
		}
		List<Staff> owed = List.of();
		for (YearMonth at : run) {
			StoredMonthInput stored = inputs.findByMonth(at).orElseThrow();
			owed = OwedOffs.after(rosters.findByMonth(at).orElseThrow(), stored.doctors(), stored.leaves(), owed);
		}
		return owed;
	}

}
