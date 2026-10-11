package com.iitm.roster.constraints;

import static com.iitm.roster.domain.StandardShifts.SHIFT_I;
import static org.assertj.core.api.Assertions.assertThat;

import com.iitm.roster.domain.HospitalCalendar;
import com.iitm.roster.domain.StaffRole;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StaffingPlanTest {

	private static final List<StaffingRequirement> FOUR = List.of(new StaffingRequirement(SHIFT_I, StaffRole.DOCTOR, 4));
	private static final List<StaffingRequirement> TWO = List.of(new StaffingRequirement(SHIFT_I, StaffRole.DOCTOR, 2));

	private final StaffingPlan plan = StaffingPlan.weekdayAndWeekend(
			new HospitalCalendar(Set.of(LocalDate.of(2026, 10, 2))), FOUR, TWO);

	@Test
	void weekdaysGetTheWeekdayNumbers() {
		assertThat(plan.on(LocalDate.of(2026, 10, 1))).isEqualTo(FOUR);
	}

	@Test
	void saturdaysSundaysAndHolidaysGetTheWeekendNumbers() {
		assertThat(plan.on(LocalDate.of(2026, 10, 3))).isEqualTo(TWO);
		assertThat(plan.on(LocalDate.of(2026, 10, 4))).isEqualTo(TWO);
		assertThat(plan.on(LocalDate.of(2026, 10, 2))).isEqualTo(TWO);
	}

	@Test
	void weekStartIsTheMondayOnOrBefore() {
		assertThat(HospitalCalendar.weekStart(LocalDate.of(2026, 10, 1))).isEqualTo(LocalDate.of(2026, 9, 28));
		assertThat(HospitalCalendar.weekStart(LocalDate.of(2026, 10, 5))).isEqualTo(LocalDate.of(2026, 10, 5));
	}

}
