package com.iitm.roster.constraints;

import static com.iitm.roster.domain.StandardShifts.NIGHT;
import static com.iitm.roster.domain.StandardShifts.SHIFT_I;

import com.iitm.roster.domain.Assignment;
import com.iitm.roster.domain.Post;
import com.iitm.roster.domain.ShiftSlot;
import com.iitm.roster.domain.ShiftType;
import com.iitm.roster.domain.Staff;
import com.iitm.roster.domain.StaffRole;
import java.time.YearMonth;
import java.util.List;

/** Synthetic doctors on the hospital's real shift times, October 2026. */
final class HospitalFixtures {

	static final YearMonth OCTOBER = YearMonth.of(2026, 10);
	static final Staff D1 = doctor(1);
	static final Staff D2 = doctor(2);
	static final Staff D3 = doctor(3);

	private HospitalFixtures() {
	}

	static Staff doctor(int n) {
		return new Staff(String.format("D%02d", n), "Test Doctor " + n, StaffRole.DOCTOR);
	}

	static Assignment at(int day, ShiftType shift, Staff staff, Post post) {
		return new Assignment(new ShiftSlot(OCTOBER.atDay(day), shift), staff, post);
	}

	/** The night and the matching Shift I ward duty. */
	static List<Assignment> night(int day, Staff staff) {
		return List.of(at(day, NIGHT, staff, Post.GENERAL), at(day, SHIFT_I, staff, Post.WARD));
	}

}
