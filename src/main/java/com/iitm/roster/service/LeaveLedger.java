package com.iitm.roster.service;

import com.iitm.roster.domain.Leave;
import com.iitm.roster.repo.MonthInputStore;
import com.iitm.roster.repo.StoredMonthInput;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
	 * Records {@code leave} on its month's stored input, so edits are checked against it.
	 * A month that has not been generated yet has no input; then nothing is recorded, and the
	 * leave reaches the month when it is generated (from the approved leave requests).
	 *
	 * @return true if the month has a stored input (whether or not the leave was already on it)
	 * @throws IllegalArgumentException if the leave crosses a month boundary, or the month has
	 *         an input and the doctor is not one of its doctors
	 */
	@Transactional
	public boolean add(Leave leave) {
		Objects.requireNonNull(leave, "leave");
		YearMonth month = YearMonth.from(leave.firstDay());
		if (!YearMonth.from(leave.lastDay()).equals(month)) {
			throw new IllegalArgumentException("leave must stay inside one month");
		}
		Optional<StoredMonthInput> found = inputs.findByMonth(month);
		if (found.isEmpty()) {
			return false;
		}
		StoredMonthInput stored = found.get();
		if (stored.doctors().stream().noneMatch(doctor -> doctor.id().equals(leave.staff().id()))) {
			throw new IllegalArgumentException(leave.staff().id() + " is not a doctor on the " + month + " roster");
		}
		if (stored.leaves().contains(leave)) {
			return true;
		}
		List<Leave> leaves = new ArrayList<>(stored.leaves());
		leaves.add(leave);
		inputs.save(new StoredMonthInput(stored.month(), stored.doctors(), stored.firstOnNights(), leaves,
				stored.calendar(), stored.weekdayPerShift(), stored.weekendPerShift()));
		return true;
	}

	/** Takes {@code leave} off the stored input of every month it touches, where it is recorded. */
	@Transactional
	public void remove(Leave leave) {
		Objects.requireNonNull(leave, "leave");
		for (YearMonth month = YearMonth.from(leave.firstDay()); !month.isAfter(YearMonth.from(leave.lastDay()));
				month = month.plusMonths(1)) {
			inputs.findByMonth(month).filter(stored -> stored.leaves().contains(leave)).ifPresent(stored -> {
				List<Leave> leaves = new ArrayList<>(stored.leaves());
				leaves.remove(leave);
				inputs.save(new StoredMonthInput(stored.month(), stored.doctors(), stored.firstOnNights(), leaves,
						stored.calendar(), stored.weekdayPerShift(), stored.weekendPerShift()));
			});
		}
	}

}
