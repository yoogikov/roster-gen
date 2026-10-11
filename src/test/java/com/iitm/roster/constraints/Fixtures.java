package com.iitm.roster.constraints;

import com.iitm.roster.domain.Assignment;
import com.iitm.roster.domain.Roster;
import com.iitm.roster.domain.RosterStatus;
import com.iitm.roster.domain.ShiftSlot;
import com.iitm.roster.domain.ShiftType;
import com.iitm.roster.domain.Staff;
import com.iitm.roster.domain.StaffRole;
import java.time.Duration;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;

/** Synthetic shifts and staff shared by the constraint tests. */
final class Fixtures {

	static final ShiftType MORNING = new ShiftType("M", LocalTime.of(8, 0), Duration.ofHours(6));
	static final ShiftType EVENING = new ShiftType("E", LocalTime.of(14, 0), Duration.ofHours(6));
	static final ShiftType NIGHT = new ShiftType("N", LocalTime.of(20, 0), Duration.ofHours(12));

	static final Staff DOC_A = new Staff("S01", "Test Doctor A", StaffRole.DOCTOR);
	static final Staff DOC_B = new Staff("S02", "Test Doctor B", StaffRole.DOCTOR);
	static final Staff NURSE_A = new Staff("S11", "Test Nurse A", StaffRole.NURSE);

	static final YearMonth JULY = YearMonth.of(2026, 7);

	private Fixtures() {
	}

	static Assignment on(YearMonth month, int day, ShiftType shift, Staff staff) {
		return new Assignment(new ShiftSlot(month.atDay(day), shift), staff);
	}

	static Assignment on(int day, ShiftType shift, Staff staff) {
		return on(JULY, day, shift, staff);
	}

	static Roster july(Assignment... assignments) {
		return new Roster(JULY, RosterStatus.DRAFT, List.of(assignments));
	}

}
