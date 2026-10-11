package com.iitm.roster.scheduler;

import static com.iitm.roster.domain.StandardShifts.SHIFT_I;
import static com.iitm.roster.domain.StandardShifts.SHIFT_II;
import static org.assertj.core.api.Assertions.assertThat;

import com.iitm.roster.constraints.StaffingPlan;
import com.iitm.roster.constraints.StaffingRequirement;
import com.iitm.roster.domain.DayOff;
import com.iitm.roster.domain.Leave;
import com.iitm.roster.domain.Staff;
import com.iitm.roster.domain.StaffRole;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WeeklyOffPlannerTest {

	private static final YearMonth OCTOBER = YearMonth.of(2026, 10);

	private static List<Staff> doctors(int count) {
		List<Staff> doctors = new ArrayList<>();
		for (int i = 1; i <= count; i++) {
			doctors.add(new Staff(String.format("D%02d", i), "Test Doctor " + i, StaffRole.DOCTOR));
		}
		return doctors;
	}

	private static WeeklyOffPlanner planner(int perShift) {
		StaffingPlan plan = StaffingPlan.constant(List.of(
				new StaffingRequirement(SHIFT_I, StaffRole.DOCTOR, perShift),
				new StaffingRequirement(SHIFT_II, StaffRole.DOCTOR, perShift)));
		return new WeeklyOffPlanner(plan, List.of(SHIFT_I, SHIFT_II));
	}

	private static List<DayOff> offsOf(List<DayOff> offs, Staff doctor) {
		return offs.stream().filter(o -> o.staff().equals(doctor)).toList();
	}

	@Test
	void plentyOfDoctorsAllGetAWeekendDayEveryWeek() {
		List<Staff> doctors = doctors(10);

		List<DayOff> offs = planner(2).plan(OCTOBER, doctors, Map.of(), List.of());

		assertThat(offs).hasSize(10 * 5);
		// 4 doctors needed a day, so 6 can be off. Weeks with both weekend days in October fit
		// everyone on Saturday or Sunday, shared evenly.
		List<DayOff> beforeLastWeek = offs.stream().filter(o -> o.date().isBefore(OCTOBER.atDay(26))).toList();
		assertThat(beforeLastWeek).allMatch(o -> o.date().getDayOfWeek() == DayOfWeek.SATURDAY
				|| o.date().getDayOfWeek() == DayOfWeek.SUNDAY);
		assertThat(beforeLastWeek.stream().filter(o -> o.date().getDayOfWeek() == DayOfWeek.SATURDAY).count()).isEqualTo(20L);
		// The last week (Mon 26 - Sat 31) has no Sunday in October: 6 on Saturday, the other 4 on Friday.
		List<DayOff> lastWeek = offs.stream().filter(o -> !o.date().isBefore(OCTOBER.atDay(26))).toList();
		assertThat(lastWeek).extracting(DayOff::date).filteredOn(d -> d.equals(OCTOBER.atDay(31))).hasSize(6);
		assertThat(lastWeek).extracting(DayOff::date).filteredOn(d -> d.equals(OCTOBER.atDay(30))).hasSize(4);
	}

	@Test
	void whenTheWeekendIsFullFridayComesNextThenMonday() {
		// 4 doctors, 1 needed per shift: each day can spare only 2.
		List<Staff> doctors = doctors(4);

		List<DayOff> offs = planner(1).plan(OCTOBER, doctors, Map.of(), List.of());

		List<LocalDate> secondWeek = offs.stream().map(DayOff::date)
				.filter(d -> !d.isBefore(LocalDate.of(2026, 10, 5)) && !d.isAfter(LocalDate.of(2026, 10, 11))).toList();
		assertThat(secondWeek).hasSize(4);
		assertThat(secondWeek).extracting(LocalDate::getDayOfWeek)
				.containsExactlyInAnyOrder(DayOfWeek.SATURDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.SUNDAY);
		// 7 doctors, 2 per shift: 3 can be off each day, so the 7th goes to Friday.
		List<DayOff> seven = planner(2).plan(OCTOBER, doctors(7), Map.of(), List.of());
		assertThat(seven.stream().map(DayOff::date)
				.filter(d -> !d.isBefore(LocalDate.of(2026, 10, 5)) && !d.isAfter(LocalDate.of(2026, 10, 11))))
				.extracting(LocalDate::getDayOfWeek).contains(DayOfWeek.FRIDAY);
	}

	@Test
	void neverOnTheirNightOrPostDutyOff() {
		List<Staff> doctors = doctors(6);
		// D01 on nights on Saturday 10th, so post-duty off on Sunday 11th.
		Map<LocalDate, Staff> nights = Map.of(OCTOBER.atDay(10), doctors.get(0));

		List<DayOff> offs = offsOf(planner(1).plan(OCTOBER, doctors, nights, List.of()), doctors.get(0));

		assertThat(offs).extracting(DayOff::date).doesNotContain(OCTOBER.atDay(10), OCTOBER.atDay(11))
				.contains(OCTOBER.atDay(9));
	}

	@Test
	void noOffDuringLeaveAndNoneNeededForAWeekAllOnLeave() {
		List<Staff> doctors = doctors(6);
		Leave wholeWeek = new Leave(doctors.get(0), OCTOBER.atDay(12), OCTOBER.atDay(18));
		Leave weekend = new Leave(doctors.get(1), OCTOBER.atDay(3), OCTOBER.atDay(4));

		List<DayOff> offs = planner(1).plan(OCTOBER, doctors, Map.of(), List.of(wholeWeek, weekend));

		assertThat(offsOf(offs, doctors.get(0))).hasSize(4).noneMatch(o -> wholeWeek.covers(o.date()));
		assertThat(offsOf(offs, doctors.get(1))).noneMatch(o -> weekend.covers(o.date()))
				.anyMatch(o -> o.date().equals(OCTOBER.atDay(2)));
	}

}
