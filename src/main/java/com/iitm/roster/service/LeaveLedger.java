package com.iitm.roster.service;

import com.iitm.roster.domain.Leave;
import com.iitm.roster.repo.MonthInputStore;
import com.iitm.roster.repo.StoredMonthInput;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adds an approved leave to the month input the roster was generated from.
 * Later edits and a regeneration then see {@code NoLeaveConflictConstraint}.
 */
@Service
public class LeaveLedger {

	private final MonthInputStore inputs;

	public LeaveLedger(MonthInputStore inputs) {
		this.inputs = inputs;
	}

	/**
	 * Records {@code leave} on its month. The doctor must already be one of that month's doctors.
	 *
	 * @throws IllegalArgumentException if the leave crosses a month boundary, or that month has no stored input
	 */
	@Transactional
	public void add(Leave leave) {
		Objects.requireNonNull(leave, "leave");
		YearMonth month = YearMonth.from(leave.firstDay());
		if (!YearMonth.from(leave.lastDay()).equals(month)) {
			throw new IllegalArgumentException("leave must stay inside one month");
		}
		StoredMonthInput stored = inputs.findByMonth(month)
				.orElseThrow(() -> new IllegalArgumentException("no month input for " + month));
		if (!stored.doctors().contains(leave.staff())) {
			throw new IllegalArgumentException(leave.staff().id() + " is not a doctor on the " + month + " roster");
		}
		List<Leave> leaves = new ArrayList<>(stored.leaves());
		leaves.add(leave);
		inputs.save(new StoredMonthInput(stored.month(), stored.doctors(), stored.firstOnNights(), leaves,
				stored.calendar(), stored.weekdayPerShift(), stored.weekendPerShift()));
	}

}
