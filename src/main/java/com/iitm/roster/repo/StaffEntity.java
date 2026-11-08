package com.iitm.roster.repo;

import com.iitm.roster.domain.Staff;
import com.iitm.roster.domain.StaffRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "staff")
class StaffEntity {

	@Id
	private String id;

	@Column(nullable = false)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private StaffRole role;

	@Column(nullable = false)
	private boolean active = true;

	protected StaffEntity() {
		// Required by JPA, which instantiates the class before setting fields; not for application code.
	}

	StaffEntity(Staff staff) {
		this.id = staff.id();
		this.active = true;
		update(staff);
	}

	void update(Staff staff) {
		this.name = staff.name();
		this.role = staff.role();
	}

	void setActive(boolean active) {
		this.active = active;
	}

	String id() {
		return id;
	}

	boolean active() {
		return active;
	}

	Staff toDomain() {
		return new Staff(id, name, role);
	}

	StaffRecord toRecord() {
		return new StaffRecord(id, name, role, active);
	}

}
