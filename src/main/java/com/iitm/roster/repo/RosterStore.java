package com.iitm.roster.repo;

import com.iitm.roster.domain.Roster;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Saves and loads monthly {@link Roster}s; there is at most one roster per month. */
@Component
@Transactional
public class RosterStore {

	private final RosterJpaRepository rosters;
	private final StaffStore staffStore;

	RosterStore(RosterJpaRepository rosters, StaffStore staffStore) {
		this.rosters = rosters;
		this.staffStore = staffStore;
	}

	/**
	 * Stores {@code roster}, replacing any roster already stored for its month. Every
	 * assigned staff member must already be saved in {@link StaffStore}.
	 */
	public void save(Roster roster) {
		List<AssignmentEmbeddable> assignments = roster.assignments().stream()
				.map(a -> new AssignmentEmbeddable(a, staffStore.require(a.staff())))
				.toList();
		RosterEntity entity = rosters.findByMonth(roster.month().toString())
				.orElseGet(() -> new RosterEntity(roster.month()));
		entity.update(roster.status(), assignments);
		rosters.save(entity);
	}

	@Transactional(readOnly = true)
	public Optional<Roster> findByMonth(YearMonth month) {
		return rosters.findByMonth(month.toString()).map(RosterEntity::toDomain);
	}

}
