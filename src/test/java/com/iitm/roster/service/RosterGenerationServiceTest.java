package com.iitm.roster.service;

import static com.iitm.roster.domain.StandardShifts.NIGHT;
import static com.iitm.roster.domain.StandardShifts.SHIFT_II;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.iitm.roster.constraints.MinStaffingConstraint;
import com.iitm.roster.constraints.PostDutyOffConstraint;
import com.iitm.roster.constraints.Violation;
import com.iitm.roster.domain.Actor;
import com.iitm.roster.domain.Assignment;
import com.iitm.roster.domain.HospitalCalendar;
import com.iitm.roster.domain.Leave;
import com.iitm.roster.domain.Post;
import com.iitm.roster.domain.Role;
import com.iitm.roster.domain.Roster;
import com.iitm.roster.domain.RosterStatus;
import com.iitm.roster.domain.ShiftSlot;
import com.iitm.roster.domain.Staff;
import com.iitm.roster.domain.StaffRole;
import com.iitm.roster.repo.AuditStore;
import com.iitm.roster.repo.MonthInputStore;
import com.iitm.roster.repo.RosterStore;
import com.iitm.roster.repo.StaffStore;
import com.iitm.roster.scheduler.GenerationResult;
import com.iitm.roster.scheduler.MonthInput;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

/**
 * Generation and editing through the Spring-wired services, on a synthetic October 2026
 * (11 made-up doctors, two holidays, a short and a long leave). Each generation takes a few seconds.
 */
@DataJpaTest
@Import({RosterGenerationService.class, RosterService.class, ServiceConfig.class,
		RosterStore.class, StaffStore.class, AuditStore.class, MonthInputStore.class})
class RosterGenerationServiceTest {

	private static final YearMonth OCTOBER = YearMonth.of(2026, 10);
	private static final Actor ADMIN = new Actor("admin", Role.ADMIN, Optional.empty());

	@Autowired
	private RosterGenerationService generation;

	@Autowired
	private RosterService editing;

	@Autowired
	private RosterStore rosters;

	@Autowired
	private MonthInputStore inputs;

	@Autowired
	private StaffStore staff;

	@Autowired
	private AuditStore audit;

	@Test
	void generatedRosterIsStoredWithItsInputAndOneAuditEvent() {
		MonthInput input = october();

		GenerationResult result = generation.generate(input, ADMIN);

		assertThat(result).isInstanceOf(GenerationResult.Ok.class);
		assertThat(rosters.findByMonth(OCTOBER)).contains(((GenerationResult.Ok) result).roster());
		assertThat(inputs.findByMonth(OCTOBER)).contains(MonthInputs.toStored(input));
		assertThat(audit.findAll()).singleElement().satisfies(event -> {
			assertThat(event.actor()).isEqualTo("admin");
			assertThat(event.summary()).startsWith("generated 2026-10: every shift filled");
		});
	}

	@Test
	void editsToAGeneratedRosterAreCheckedAgainstTheHospitalRules() {
		generation.generate(october(), ADMIN);
		Roster roster = rosters.findByMonth(OCTOBER).orElseThrow();
		LocalDate monday = LocalDate.of(2026, 10, 5);
		LocalDate tuesday = monday.plusDays(1);
		Staff nightDoctor = roster.assignmentsOn(new ShiftSlot(monday, NIGHT)).get(0).staff();
		Assignment generalOnTuesday = roster.assignmentsOn(new ShiftSlot(tuesday, SHIFT_II)).stream()
				.filter(a -> a.post() == Post.GENERAL)
				.findFirst()
				.orElseThrow();

		EditResult duringPostDutyOff = editing.applyEdit(
				new EditCommand.Assign(new ShiftSlot(tuesday, SHIFT_II), nightDoctor.id()), ADMIN);
		EditResult belowWeekdayMinimum = editing.applyEdit(
				new EditCommand.Unassign(generalOnTuesday.slot(), generalOnTuesday.staff().id()), ADMIN);

		assertThat(rejectedIds(duringPostDutyOff)).contains(PostDutyOffConstraint.ID);
		assertThat(rejectedIds(belowWeekdayMinimum)).contains(MinStaffingConstraint.ID);
		assertThat(rosters.findByMonth(OCTOBER)).contains(roster);
		assertThat(audit.findAll()).hasSize(1);
	}

	@Test
	void publishedMonthIsNotRegenerated() {
		MonthInput input = october();
		for (Staff doctor : input.doctors()) {
			staff.save(doctor);
		}
		rosters.save(Roster.empty(OCTOBER).withStatus(RosterStatus.PUBLISHED));

		assertThatThrownBy(() -> generation.generate(input, ADMIN))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("published");
		assertThat(inputs.findByMonth(OCTOBER)).isEmpty();
		assertThat(audit.findAll()).isEmpty();
	}

	private static List<String> rejectedIds(EditResult result) {
		assertThat(result).isInstanceOf(EditResult.Rejected.class);
		return ((EditResult.Rejected) result).violations().stream().map(Violation::constraintId).toList();
	}

	private static MonthInput october() {
		List<Staff> doctors = new ArrayList<>();
		for (int i = 1; i <= 11; i++) {
			doctors.add(new Staff(String.format("D%02d", i), "Test Doctor " + i, StaffRole.DOCTOR));
		}
		List<Leave> leaves = List.of(
				new Leave(doctors.get(2), LocalDate.of(2026, 10, 7), LocalDate.of(2026, 10, 9)),
				new Leave(doctors.get(4), LocalDate.of(2026, 10, 22), LocalDate.of(2026, 11, 3)));
		HospitalCalendar calendar = new HospitalCalendar(Set.of(LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 20)));
		return new MonthInput(OCTOBER, doctors, doctors.get(0), leaves, calendar, 4, 2);
	}

}
