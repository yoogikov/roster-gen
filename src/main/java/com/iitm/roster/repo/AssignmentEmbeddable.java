package com.iitm.roster.repo;

import com.iitm.roster.domain.Assignment;
import com.iitm.roster.domain.Post;
import com.iitm.roster.domain.ShiftSlot;
import com.iitm.roster.domain.ShiftType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * One {@link Assignment} as columns. The shift type is stored inline (code, start, length)
 * because {@code domain} has no shift-type table; the staff member is a foreign key.
 * Post-duty off is never stored: it is always the day after a night.
 */
@Embeddable
class AssignmentEmbeddable {

	@Column(name = "slot_date", nullable = false)
	private LocalDate date;

	@Column(name = "shift_code", nullable = false)
	private String shiftCode;

	@Column(name = "shift_start", nullable = false)
	private LocalTime shiftStart;

	@Column(name = "shift_length", nullable = false)
	private Duration shiftLength;

	@ManyToOne(optional = false)
	@JoinColumn(name = "staff_id", nullable = false)
	private StaffEntity staff;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Post post;

	protected AssignmentEmbeddable() {
		// Required by JPA, which instantiates the class before setting fields; not for application code.
	}

	AssignmentEmbeddable(Assignment assignment, StaffEntity staff) {
		ShiftSlot slot = assignment.slot();
		this.date = slot.date();
		this.shiftCode = slot.shift().code();
		this.shiftStart = slot.shift().start();
		this.shiftLength = slot.shift().length();
		this.staff = staff;
		this.post = assignment.post();
	}

	Assignment toDomain() {
		ShiftType shift = new ShiftType(shiftCode, shiftStart, shiftLength);
		return new Assignment(new ShiftSlot(date, shift), staff.toDomain(), post);
	}

}
