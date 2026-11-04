package com.iitm.roster.repo;

import com.iitm.roster.domain.ChangeRequest;
import com.iitm.roster.domain.Post;
import com.iitm.roster.domain.RequestState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;

/** An edit waiting for approval. Staff ids are stored as text; the edit looks the people up when it is applied. */
@Entity
@Table(name = "change_request")
class ChangeRequestEntity {

	@Id
	private String id;

	@Column(name = "roster_month", nullable = false, length = 7)
	private String month;

	@Column(nullable = false)
	private String type;

	@Column(name = "slot_date", nullable = false)
	private LocalDate date;

	@Column(name = "shift_code", nullable = false)
	private String shiftCode;

	private String staffId;

	private String fromStaffId;

	private String toStaffId;

	@Enumerated(EnumType.STRING)
	private Post post;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private RequestState state;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected ChangeRequestEntity() {
		// Required by JPA, which instantiates the class before setting fields; not for application code.
	}

	ChangeRequestEntity(String id) {
		this.id = id;
	}

	void update(ChangeRequest request) {
		this.month = request.month().toString();
		this.type = request.type();
		this.date = request.date();
		this.shiftCode = request.shiftCode();
		this.staffId = request.staffId();
		this.fromStaffId = request.fromStaffId();
		this.toStaffId = request.toStaffId();
		this.post = request.post();
		this.state = request.state();
		this.createdAt = request.createdAt();
	}

	ChangeRequest toDomain() {
		return new ChangeRequest(id, YearMonth.parse(month), type, date, shiftCode, staffId, fromStaffId, toStaffId, post,
				state, createdAt);
	}

}
