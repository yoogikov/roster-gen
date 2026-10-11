package com.iitm.roster.repo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.iitm.roster.domain.HospitalCalendar;
import com.iitm.roster.domain.Leave;
import com.iitm.roster.domain.Staff;
import com.iitm.roster.domain.StaffRole;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({MonthInputStore.class, StaffStore.class})
class MonthInputStoreTest {

	private static final YearMonth OCTOBER = YearMonth.of(2026, 10);

	private static final Staff D01 = new Staff("D01", "Test Doctor 1", StaffRole.DOCTOR);
	private static final Staff D02 = new Staff("D02", "Test Doctor 2", StaffRole.DOCTOR);
	private static final Staff D03 = new Staff("D03", "Test Doctor 3", StaffRole.DOCTOR);

	private static final HospitalCalendar CALENDAR =
			new HospitalCalendar(Set.of(LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 20)));

	@Autowired
	private MonthInputStore inputs;

	@Autowired
	private StaffStore staff;

	@Autowired
	private TestEntityManager em;

	@BeforeEach
	void saveStaff() {
		staff.save(D01);
		staff.save(D02);
		staff.save(D03);
	}

	@Test
	void inputSurvivesSaveAndReloadWithRotationOrderKept() {
		StoredMonthInput input = new StoredMonthInput(OCTOBER, List.of(D03, D01, D02), D01,
				List.of(new Leave(D02, LocalDate.of(2026, 10, 7), LocalDate.of(2026, 10, 9)),
						new Leave(D03, LocalDate.of(2026, 10, 22), LocalDate.of(2026, 11, 3))),
				CALENDAR, 4, 2);

		inputs.save(input);
		flushAndClear();

		assertThat(inputs.findByMonth(OCTOBER)).contains(input);
	}

	@Test
	void savingTheSameMonthAgainReplacesTheInput() {
		inputs.save(new StoredMonthInput(OCTOBER, List.of(D01, D02, D03), D01,
				List.of(new Leave(D02, LocalDate.of(2026, 10, 7), LocalDate.of(2026, 10, 9))), CALENDAR, 4, 2));
		StoredMonthInput changed = new StoredMonthInput(OCTOBER, List.of(D02, D03), D03, List.of(),
				HospitalCalendar.noHolidays(), 3, 3);

		inputs.save(changed);
		flushAndClear();

		assertThat(inputs.findByMonth(OCTOBER)).contains(changed);
		assertThat(em.getEntityManager().createQuery("select count(m) from MonthInputEntity m", Long.class)
				.getSingleResult()).isEqualTo(1L);
	}

	@Test
	void monthNeverSavedIsEmpty() {
		assertThat(inputs.findByMonth(OCTOBER)).isEmpty();
	}

	@Test
	void doctorNotSavedIsRejected() {
		Staff stranger = new Staff("D99", "Test Unsaved", StaffRole.DOCTOR);
		StoredMonthInput input = new StoredMonthInput(OCTOBER, List.of(D01, stranger), D01, List.of(), CALENDAR, 4, 2);

		assertThatThrownBy(() -> inputs.save(input))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("D99");
	}

	private void flushAndClear() {
		em.flush();
		em.clear();
	}

}
