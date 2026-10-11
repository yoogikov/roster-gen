package com.iitm.roster.repo;

import com.iitm.roster.domain.Staff;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Saves and loads {@link Staff}. Saving an existing id updates the name and role. */
@Component
@Transactional
public class StaffStore {

	private final StaffJpaRepository staff;

	StaffStore(StaffJpaRepository staff) {
		this.staff = staff;
	}

	public void save(Staff member) {
		staff.findById(member.id()).ifPresentOrElse(
				existing -> existing.update(member),
				() -> staff.save(new StaffEntity(member)));
	}

	@Transactional(readOnly = true)
	public Optional<Staff> findById(String id) {
		return staff.findById(id).map(StaffEntity::toDomain);
	}

	/** Everyone, ordered by id. */
	@Transactional(readOnly = true)
	public List<Staff> findAll() {
		return staff.findAllByOrderByIdAsc().stream().map(StaffEntity::toDomain).toList();
	}

	/**
	 * The stored row for {@code member}. Throws if it was never saved, or was saved with a
	 * different name or role, since a roster reloaded from it would then not equal the one saved.
	 */
	StaffEntity require(Staff member) {
		StaffEntity entity = staff.findById(member.id())
				.orElseThrow(() -> new IllegalArgumentException("unknown staff: " + member.id()));
		if (!entity.toDomain().equals(member)) {
			throw new IllegalArgumentException("staff " + member.id() + " does not match the stored record");
		}
		return entity;
	}

}
