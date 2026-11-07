package com.iitm.roster.scheduler;

import static com.iitm.roster.domain.StandardShifts.NIGHT;
import static com.iitm.roster.domain.StandardShifts.SHIFT_I;
import static org.assertj.core.api.Assertions.assertThat;

import com.iitm.roster.domain.Assignment;
import com.iitm.roster.domain.Post;
import com.iitm.roster.domain.Roster;
import com.iitm.roster.domain.ShiftSlot;
import com.iitm.roster.domain.Staff;
import com.iitm.roster.domain.StaffRole;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Who starts next month's nights, continuing the rotation from last month's roster. */
class NightCarryOverTest {

	private static final YearMonth NOVEMBER = YearMonth.of(2026, 11);
	private static final List<Staff> ORDER = doctors(5);

	@Test
	void theDoctorAfterLastMonthsFinalNightStarts() {
		Roster november = nights(ORDER.get(0), ORDER.get(1), ORDER.get(2));

		assertThat(NightRotation.firstAfter(november, NIGHT, ORDER)).contains(ORDER.get(3));
	}

	@Test
	void theOrderWrapsFromTheLastDoctorToTheFirst() {
		Roster november = nights(ORDER.get(2), ORDER.get(3), ORDER.get(4));

		assertThat(NightRotation.firstAfter(november, NIGHT, ORDER)).contains(ORDER.get(0));
	}

	@Test
	void anUnfilledLastNightFallsBackToTheLatestNightThatWasFilled() {
		Roster november = Roster.empty(NOVEMBER)
				.withAssignment(night(28, ORDER.get(1)))
				.withAssignment(night(29, ORDER.get(2)));

		assertThat(NightRotation.firstAfter(november, NIGHT, ORDER)).contains(ORDER.get(3));
	}

	@Test
	void aDoctorWhoHasLeftIsSkippedAndTheRotationContinuesAfterTheLatestWhoStayed() {
		Staff leaver = new Staff("D99", "Test Doctor 99", StaffRole.DOCTOR);
		Roster november = nights(ORDER.get(1), ORDER.get(2), leaver);

		assertThat(NightRotation.firstAfter(november, NIGHT, ORDER)).contains(ORDER.get(3));
	}

	@Test
	void dayShiftsDoNotCountAsNights() {
		Roster november = Roster.empty(NOVEMBER)
				.withAssignment(night(28, ORDER.get(0)))
				.withAssignment(night(29, ORDER.get(1)))
				.withAssignment(new Assignment(new ShiftSlot(NOVEMBER.atDay(30), SHIFT_I), ORDER.get(4), Post.WARD));

		assertThat(NightRotation.firstAfter(november, NIGHT, ORDER)).contains(ORDER.get(2));
	}

	@Test
	void aMonthWithNoNightsByAnyoneOnTheListGivesNoSuggestion() {
		Staff leaver = new Staff("D99", "Test Doctor 99", StaffRole.DOCTOR);

		assertThat(NightRotation.firstAfter(Roster.empty(NOVEMBER), NIGHT, ORDER)).isEmpty();
		assertThat(NightRotation.firstAfter(nights(leaver), NIGHT, ORDER)).isEmpty();
	}

	/** Nights on the last days of November, in the order given, ending on the 30th. */
	private static Roster nights(Staff... doctors) {
		Roster roster = Roster.empty(NOVEMBER);
		int firstDay = 30 - doctors.length + 1;
		for (int i = 0; i < doctors.length; i++) {
			roster = roster.withAssignment(night(firstDay + i, doctors[i]));
		}
		return roster;
	}

	private static Assignment night(int day, Staff doctor) {
		return new Assignment(new ShiftSlot(NOVEMBER.atDay(day), NIGHT), doctor, Post.WARD);
	}

	private static List<Staff> doctors(int count) {
		List<Staff> doctors = new ArrayList<>();
		for (int i = 1; i <= count; i++) {
			doctors.add(new Staff(String.format("D%02d", i), "Test Doctor " + i, StaffRole.DOCTOR));
		}
		return List.copyOf(doctors);
	}

}
