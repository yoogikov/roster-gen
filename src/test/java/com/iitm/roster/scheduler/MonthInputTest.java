package com.iitm.roster.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.iitm.roster.domain.HospitalCalendar;
import com.iitm.roster.domain.Staff;
import com.iitm.roster.domain.StaffRole;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

class MonthInputTest {

	private static final YearMonth OCTOBER = YearMonth.of(2026, 10);

	@Test
	void standardStartsNightsWithTheFirstDoctorAndUsesFourAndTwo() {
		Staff first = new Staff("D01", "Test Doctor 1", StaffRole.DOCTOR);
		Staff second = new Staff("D02", "Test Doctor 2", StaffRole.DOCTOR);

		MonthInput input = MonthInput.standard(OCTOBER, List.of(first, second), List.of(), HospitalCalendar.noHolidays());

		assertThat(input.firstOnNights()).isEqualTo(first);
		assertThat(input.weekdayPerShift()).isEqualTo(4);
		assertThat(input.weekendPerShift()).isEqualTo(2);
	}

	@Test
	void standardNeedsAtLeastOneDoctor() {
		assertThatThrownBy(() -> MonthInput.standard(OCTOBER, List.of(), List.of(), HospitalCalendar.noHolidays()))
				.isInstanceOf(IllegalArgumentException.class);
	}

}
