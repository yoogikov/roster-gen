package com.iitm.roster.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.iitm.roster.domain.Leave;
import com.iitm.roster.domain.Staff;
import com.iitm.roster.domain.StaffRole;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NightRotationTest {

	private static final YearMonth OCTOBER = YearMonth.of(2026, 10);

	private static List<Staff> doctors(int count) {
		List<Staff> doctors = new ArrayList<>();
		for (int i = 1; i <= count; i++) {
			doctors.add(new Staff(String.format("D%02d", i), "Test Doctor " + i, StaffRole.DOCTOR));
		}
		return doctors;
	}

	private static List<String> ids(Map<LocalDate, Staff> nights, int fromDay, int toDay) {
		List<String> ids = new ArrayList<>();
		for (int day = fromDay; day <= toDay; day++) {
			Staff doctor = nights.get(OCTOBER.atDay(day));
			ids.add(doctor == null ? "-" : doctor.id());
		}
		return ids;
	}

	private static Leave leave(Staff doctor, int firstDay, int lastDay) {
		return new Leave(doctor, OCTOBER.atDay(firstDay), OCTOBER.atDay(lastDay));
	}

	@Test
	void followsTheOrderRoundAndRound() {
		Map<LocalDate, Staff> nights = NightRotation.plan(OCTOBER, doctors(3), doctors(3).get(0), List.of());

		assertThat(nights).hasSize(31);
		assertThat(ids(nights, 1, 7)).containsExactly("D01", "D02", "D03", "D01", "D02", "D03", "D01");
	}

	@Test
	void startsWithWhoeverIsNext() {
		List<Staff> doctors = doctors(3);

		assertThat(ids(NightRotation.plan(OCTOBER, doctors, doctors.get(2), List.of()), 1, 4))
				.containsExactly("D03", "D01", "D02", "D03");
	}

	@Test
	void shortLeaveSwapsWithTheNextDoctorAndTheOrderCarriesOn() {
		List<Staff> doctors = doctors(4);
		// D02's turn is the 2nd; they are away 2nd-3rd, so D03 does the 2nd and D02 takes D03's turn,
		// but D02 is still away on the 3rd, so D04 does it and D02 does the 4th.
		Map<LocalDate, Staff> nights = NightRotation.plan(OCTOBER, doctors, doctors.get(0), List.of(leave(doctors.get(1), 2, 3)));

		assertThat(ids(nights, 1, 8)).containsExactly("D01", "D03", "D04", "D02", "D01", "D03", "D04", "D02");
	}

	@Test
	void exactlyTenDaysIsStillShortLeave() {
		List<Staff> doctors = doctors(3);
		Map<LocalDate, Staff> nights = NightRotation.plan(OCTOBER, doctors, doctors.get(0), List.of(leave(doctors.get(0), 1, 10)));

		// D01 keeps being swapped forward and does the first night after the leave.
		assertThat(nights.get(OCTOBER.atDay(11)).id()).isEqualTo("D01");
		assertThat(ids(nights, 1, 10)).doesNotContain("D01");
	}

	@Test
	void longLeaveMeansMoreThanTenDaysCountingBothEnds() {
		Staff doctor = doctors(1).get(0);

		assertThat(NightRotation.isLong(leave(doctor, 1, 10))).isFalse();
		assertThat(NightRotation.isLong(leave(doctor, 1, 11))).isTrue();
	}

	@Test
	void longLeaveTakesTheDoctorOutAndPutsThemBackOnTheirReturnDay() {
		List<Staff> doctors = doctors(4);
		// D02 is away 3rd to 14th (12 days) and returns on the 15th.
		Map<LocalDate, Staff> nights = NightRotation.plan(OCTOBER, doctors, doctors.get(0), List.of(leave(doctors.get(1), 3, 14)));

		assertThat(ids(nights, 1, 2)).containsExactly("D01", "D02");
		assertThat(ids(nights, 3, 14)).doesNotContain("D02");
		assertThat(nights.get(OCTOBER.atDay(15)).id()).isEqualTo("D02");
		// While D02 is away the other three rotate; D03 would have been next on the 15th, so
		// after D02's night the order carries on from D03.
		assertThat(ids(nights, 3, 8)).containsExactly("D03", "D04", "D01", "D03", "D04", "D01");
		assertThat(ids(nights, 15, 18)).containsExactly("D02", "D03", "D04", "D01");
	}

	@Test
	void nobodyDoesTwoNightsInARowAndADayNobodyCanTakeIsLeftOut() {
		List<Staff> doctors = doctors(2);
		// D02 is away on the 2nd; D01 did the 1st, so the 2nd has nobody.
		Map<LocalDate, Staff> nights = NightRotation.plan(OCTOBER, doctors, doctors.get(0), List.of(leave(doctors.get(1), 2, 2)));

		assertThat(ids(nights, 1, 4)).containsExactly("D01", "-", "D02", "D01");
	}

	@Test
	void firstDoctorMustBeInTheRotation() {
		Staff outsider = new Staff("X", "Test Outsider", StaffRole.DOCTOR);

		assertThatThrownBy(() -> NightRotation.plan(OCTOBER, doctors(2), outsider, List.of()))
				.isInstanceOf(IllegalArgumentException.class);
	}

}
