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
import com.iitm.roster.domain.DayOff;
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
import com.iitm.roster.repo.LeaveRequestStore;
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
		RosterStore.class, StaffStore.class, AuditStore.class, MonthInputStore.class, LeaveRequestStore.class})
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

	@Test
	void theMonthsHolidaysAreKeptAndSurviveGeneratingAgain() {
		generation.generate(october(), ADMIN);

		generation.regenerate(OCTOBER, ADMIN);

		assertThat(generation.calendarFor(OCTOBER).holidays())
				.containsExactlyInAnyOrder(LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 20));
		assertThat(inputs.findByMonth(OCTOBER).orElseThrow().calendar().holidays()).hasSize(2);
	}

	@Test
	void aMonthNeverGeneratedHasNoHolidays() {
		assertThat(generation.calendarFor(YearMonth.of(2027, 3)).holidays()).isEmpty();
	}

	@Test
	void nextMonthsNightsContinueAfterTheLastNightOfThisMonth() {
		MonthInput input = october();
		generation.generate(input, ADMIN);
		Staff lastNight = rosters.findByMonth(OCTOBER).orElseThrow()
				.assignmentsOn(new ShiftSlot(OCTOBER.atEndOfMonth(), NIGHT)).get(0).staff();
		int at = input.doctors().indexOf(lastNight);

		assertThat(generation.continueNights(OCTOBER.plusMonths(1), input.doctors()))
				.contains(input.doctors().get((at + 1) % input.doctors().size()));
	}

	@Test
	void withNoRosterForLastMonthThereIsNothingToContinue() {
		assertThat(generation.continueNights(OCTOBER, october().doctors())).isEmpty();
	}

	@Test
	void lastMonthsFinalNightDoctorIsOffOnTheFirstEvenWhenTypedAsFirstOnNights() {
		MonthInput input = october();
		generation.generate(input, ADMIN);
		Staff lastNight = lastNightOfOctober();
		YearMonth november = OCTOBER.plusMonths(1);

		generation.generate(new MonthInput(november, input.doctors(), lastNight, List.of(), HospitalCalendar.noHolidays(), 4, 2),
				ADMIN);

		Roster roster = rosters.findByMonth(november).orElseThrow();
		assertThat(roster.assignmentsFor(lastNight)).noneMatch(a -> a.slot().date().equals(november.atDay(1)));
		assertThat(roster.assignmentsOn(new ShiftSlot(november.atDay(1), NIGHT))).extracting(Assignment::staff)
				.doesNotContain(lastNight);
	}

	@Test
	void anEditPuttingLastMonthsFinalNightDoctorOnTheFirstIsRejected() {
		MonthInput input = october();
		generation.generate(input, ADMIN);
		Staff lastNight = lastNightOfOctober();
		YearMonth november = OCTOBER.plusMonths(1);
		generation.generate(new MonthInput(november, input.doctors(), input.doctors().get(0), List.of(),
				HospitalCalendar.noHolidays(), 4, 2), ADMIN);

		EditResult edit = editing.applyEdit(
				new EditCommand.Assign(new ShiftSlot(november.atDay(1), SHIFT_II), lastNight.id()), ADMIN);

		assertThat(rejectedIds(edit)).contains(PostDutyOffConstraint.ID);
	}

	private Staff lastNightOfOctober() {
		return rosters.findByMonth(OCTOBER).orElseThrow()
				.assignmentsOn(new ShiftSlot(OCTOBER.atEndOfMonth(), NIGHT)).get(0).staff();
	}

	/**
	 * Found by {@code HospitalRosterProperties} (shrunk): 10 doctors, February 2026, one leave at the
	 * end of the last, six-day week. That week has room for only 9 weekly offs, so one doctor gets
	 * none. That off is owed to March, not a broken rule.
	 */
	@Test
	void aWeeklyOffWithNoRoomIsOwedAndRecordedNotBroken() {
		YearMonth february = YearMonth.of(2026, 2);
		List<Staff> doctors = doctors(10);
		MonthInput input = new MonthInput(february, doctors, doctors.get(0),
				List.of(new Leave(doctors.get(5), february.atDay(27), february.atDay(28))),
				new HospitalCalendar(Set.of(february.atDay(2))), 4, 2);

		GenerationResult result = generation.generate(input, ADMIN);

		assertThat(result).isInstanceOf(GenerationResult.Ok.class);
		assertThat(generation.brokenRules(february)).isEmpty();
		assertThat(generation.owedOffsAfter(february)).containsExactly(doctors.get(9));
		assertThat(audit.findAll()).singleElement().satisfies(event -> assertThat(event.summary())
				.endsWith("; weekly offs owed to next month: Test Doctor 10"));
	}

	/** December 2026 ends on a Thursday: that four-day week has room for 8 of 11 weekly offs. */
	@Test
	void offsThatDoNotFitAtTheEndOfAMonthAreGivenAtTheStartOfTheNext() {
		YearMonth december = YearMonth.of(2026, 12);
		YearMonth january = december.plusMonths(1);
		List<Staff> doctors = doctors(11);
		generation.generate(month(december, doctors), ADMIN);
		List<Staff> owed = generation.owedOffsAfter(december);
		assertThat(owed).hasSize(3);
		assertThat(generation.brokenRules(december)).isEmpty();

		generation.generate(month(january, doctors), ADMIN);

		Roster roster = rosters.findByMonth(january).orElseThrow();
		assertThat(roster.weeklyOffs()).filteredOn(off -> off.date().isBefore(january.atDay(4)))
				.extracting(DayOff::staff).containsExactlyInAnyOrderElementsOf(owed);
		assertThat(generation.owedOffsAfter(january)).isEmpty();
		assertThat(generation.owedOffsInto(january)).containsExactlyElementsOf(owed);
		assertThat(generation.brokenRules(january)).isEmpty();
	}

	@Test
	void whatIsOwedIsReadAgainFromTheRostersWhenAMonthIsGeneratedAgain() {
		YearMonth december = YearMonth.of(2026, 12);
		YearMonth january = december.plusMonths(1);
		List<Staff> doctors = doctors(11);
		generation.generate(month(december, doctors), ADMIN);
		generation.generate(month(january, doctors), ADMIN);
		Roster first = rosters.findByMonth(january).orElseThrow();

		generation.regenerate(january, ADMIN);

		assertThat(rosters.findByMonth(january).orElseThrow().weeklyOffs()).isEqualTo(first.weeklyOffs());
	}

	@Test
	void whatNoMonthCouldGiveKeepsAddingUpAcrossMonths() {
		YearMonth december = YearMonth.of(2026, 12);
		YearMonth january = december.plusMonths(1);
		List<Staff> doctors = doctors(1);
		staff.save(doctors.get(0));
		for (YearMonth month : List.of(december, january)) {
			inputs.save(MonthInputs.toStored(month(month, doctors)));
			rosters.save(Roster.empty(month));
		}

		// Five counted weeks in December and four in January, with no offs at all.
		assertThat(generation.owedOffsAfter(january)).hasSize(9);
	}

	@Test
	void aDoctorNoLongerOnTheListIsNotOwedIntoTheMonth() {
		YearMonth december = YearMonth.of(2026, 12);
		YearMonth january = december.plusMonths(1);
		List<Staff> doctors = doctors(11);
		generation.generate(month(december, doctors), ADMIN);
		Staff leaving = generation.owedOffsAfter(december).get(0);
		List<Staff> staying = new ArrayList<>(doctors);
		staying.remove(leaving);

		generation.generate(month(january, staying), ADMIN);

		assertThat(generation.owedOffsInto(january)).hasSize(2).doesNotContain(leaving);
		assertThat(generation.owedOffsInto(YearMonth.of(2030, 1))).isEmpty();
	}

	@Test
	void aMonthWithNoRosterBeforeItIsOwedNothing() {
		List<Staff> doctors = doctors(11);
		YearMonth january = YearMonth.of(2027, 1);

		generation.generate(month(january, doctors), ADMIN);

		assertThat(rosters.findByMonth(january).orElseThrow().weeklyOffs())
				.noneMatch(off -> off.date().isBefore(january.atDay(4)));
		assertThat(generation.owedOffsAfter(YearMonth.of(2030, 1))).isEmpty();
	}

	private static List<Staff> doctors(int count) {
		List<Staff> doctors = new ArrayList<>();
		for (int i = 1; i <= count; i++) {
			doctors.add(new Staff(String.format("D%02d", i), "Test Doctor " + i, StaffRole.DOCTOR));
		}
		return doctors;
	}

	private static MonthInput month(YearMonth month, List<Staff> doctors) {
		return new MonthInput(month, doctors, doctors.get(0), List.of(), HospitalCalendar.noHolidays(), 4, 2);
	}

	@Test
	void aRosterThatKeepsEveryHardRuleReportsNoneBroken() {
		generation.generate(october(), ADMIN);

		assertThat(generation.brokenRules(OCTOBER)).isEmpty();
		assertThat(generation.brokenRules(YearMonth.of(2027, 5))).isEmpty();
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
