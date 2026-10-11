package com.iitm.roster.repo;

import com.iitm.roster.domain.Leave;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.LocalDate;

/** One leave as columns; it may run past the end of the month it is stored with. */
@Embeddable
class LeaveEmbeddable {

	@ManyToOne(optional = false)
	@JoinColumn(name = "staff_id", nullable = false)
	private StaffEntity staff;

	@Column(name = "first_day", nullable = false)
	private LocalDate firstDay;

	@Column(name = "last_day", nullable = false)
	private LocalDate lastDay;

	protected LeaveEmbeddable() {
		// Required by JPA, which instantiates the class before setting fields; not for application code.
	}

	LeaveEmbeddable(Leave leave, StaffEntity staff) {
		this.staff = staff;
		this.firstDay = leave.firstDay();
		this.lastDay = leave.lastDay();
	}

	Leave toDomain() {
		return new Leave(staff.toDomain(), firstDay, lastDay);
	}

}
