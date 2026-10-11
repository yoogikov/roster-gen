package com.iitm.roster.repo;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Saves and loads the input each month's roster was generated from; at most one per month. */
@Component
@Transactional
public class MonthInputStore {

	private final MonthInputJpaRepository inputs;
	private final StaffStore staffStore;

	MonthInputStore(MonthInputJpaRepository inputs, StaffStore staffStore) {
		this.inputs = inputs;
		this.staffStore = staffStore;
	}

	/** Stores {@code input}, replacing any input already stored for its month. Every doctor must already be saved. */
	public void save(StoredMonthInput input) {
		List<StaffEntity> doctors = input.doctors().stream().map(staffStore::require).toList();
		List<LeaveEmbeddable> leaves = input.leaves().stream()
				.map(leave -> new LeaveEmbeddable(leave, staffStore.require(leave.staff())))
				.toList();
		MonthInputEntity entity = inputs.findByMonth(input.month().toString())
				.orElseGet(() -> new MonthInputEntity(input.month()));
		entity.update(input, doctors, staffStore.require(input.firstOnNights()), leaves);
		inputs.save(entity);
	}

	@Transactional(readOnly = true)
	public Optional<StoredMonthInput> findByMonth(YearMonth month) {
		return inputs.findByMonth(month.toString()).map(MonthInputEntity::toDomain);
	}

}
