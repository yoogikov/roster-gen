package com.iitm.roster.repo;

import com.iitm.roster.domain.DayOff;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.LocalDate;

/** One weekly off as columns. */
@Embeddable
class DayOffEmbeddable {

	@ManyToOne(optional = false)
	@JoinColumn(name = "staff_id", nullable = false)
	private StaffEntity staff;

	@Column(name = "off_date", nullable = false)
	private LocalDate date;

	protected DayOffEmbeddable() {
		// Required by JPA, which instantiates the class before setting fields; not for application code.
	}

	DayOffEmbeddable(DayOff off, StaffEntity staff) {
		this.staff = staff;
		this.date = off.date();
	}

	DayOff toDomain() {
		return new DayOff(staff.toDomain(), date);
	}

}
