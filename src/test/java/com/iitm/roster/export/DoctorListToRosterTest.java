package com.iitm.roster.export;

import static com.iitm.roster.domain.StandardShifts.NIGHT;
import static com.iitm.roster.domain.StandardShifts.SHIFT_I;
import static com.iitm.roster.domain.StandardShifts.SHIFT_II;
import static org.assertj.core.api.Assertions.assertThat;

import com.iitm.roster.constraints.Constraint;
import com.iitm.roster.constraints.Severity;
import com.iitm.roster.domain.HospitalCalendar;
import com.iitm.roster.domain.Roster;
import com.iitm.roster.scheduler.GenerationResult;
import com.iitm.roster.scheduler.HospitalRosterGenerator;
import com.iitm.roster.scheduler.MonthInput;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The whole flow the app offers: a doctor list in, a month's roster out. */
class DoctorListToRosterTest {

	@Test
	void theFixtureListGivesAFullOctoberWithNoHardViolations() throws Exception {
		DoctorList list;
		try (InputStream in = getClass().getResourceAsStream("/fixtures/doctors-october-2026.csv")) {
			list = new CsvDoctorListReader().read(in);
		}
		HospitalCalendar calendar = new HospitalCalendar(Set.of(LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 20)));
		MonthInput input = MonthInput.standard(YearMonth.of(2026, 10), list.doctors(), list.leaves(), calendar);
		HospitalRosterGenerator generator = new HospitalRosterGenerator(SHIFT_I, SHIFT_II, NIGHT);

		GenerationResult result = generator.generate(input);

		assertThat(result).isInstanceOf(GenerationResult.Ok.class);
		Roster roster = ((GenerationResult.Ok) result).roster();
		for (Constraint constraint : generator.constraints(input)) {
			if (constraint.severity() == Severity.HARD) {
				assertThat(constraint.check(roster)).as(constraint.id()).isEmpty();
			}
		}
	}

}
