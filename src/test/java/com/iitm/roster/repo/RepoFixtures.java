package com.iitm.roster.repo;

import com.iitm.roster.domain.Assignment;
import com.iitm.roster.domain.ShiftSlot;
import com.iitm.roster.domain.ShiftType;
import com.iitm.roster.domain.Staff;
import com.iitm.roster.domain.StaffRole;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;

/** Synthetic staff and shifts for the persistence tests. Not real hospital data. */
final class RepoFixtures {

	static final ShiftType MORNING = new ShiftType("M", LocalTime.of(8, 0), Duration.ofHours(6));
	static final ShiftType NIGHT = new ShiftType("N", LocalTime.of(20, 0), Duration.ofHours(12));

	static final Staff DOC_A = new Staff("S01", "Test Doctor A", StaffRole.DOCTOR);
	static final Staff DOC_B = new Staff("S02", "Test Doctor B", StaffRole.DOCTOR);
	static final Staff NURSE_C = new Staff("S03", "Test Nurse C", StaffRole.NURSE);

	private RepoFixtures() {
	}

	static Assignment on(LocalDate date, ShiftType shift, Staff staff) {
		return new Assignment(new ShiftSlot(date, shift), staff);
	}

}
