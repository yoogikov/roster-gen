package com.iitm.roster.repo;

import com.iitm.roster.domain.LeaveRequest;
import com.iitm.roster.domain.RequestState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "leave_request")
class LeaveRequestEntity {

	@Id
	private String id;

	@ManyToOne(optional = false)
	@JoinColumn(name = "staff_id", nullable = false)
	private StaffEntity staff;

	@Column(name = "first_day", nullable = false)
	private LocalDate firstDay;

	@Column(name = "last_day", nullable = false)
	private LocalDate lastDay;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private RequestState state;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected LeaveRequestEntity() {
		// Required by JPA, which instantiates the class before setting fields; not for application code.
	}

	LeaveRequestEntity(String id) {
		this.id = id;
	}

	void update(LeaveRequest request, StaffEntity staffMember) {
		this.staff = staffMember;
		this.firstDay = request.firstDay();
		this.lastDay = request.lastDay();
		this.state = request.state();
		this.createdAt = request.createdAt();
	}

	LeaveRequest toDomain() {
		return new LeaveRequest(id, staff.toDomain(), firstDay, lastDay, state, createdAt);
	}

}
