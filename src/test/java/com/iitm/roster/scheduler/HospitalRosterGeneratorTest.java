package com.iitm.roster.scheduler;

import static com.iitm.roster.domain.StandardShifts.NIGHT;
import static com.iitm.roster.domain.StandardShifts.SHIFT_I;
import static com.iitm.roster.domain.StandardShifts.SHIFT_II;
import static org.assertj.core.api.Assertions.assertThat;

import com.iitm.roster.constraints.Constraint;
import com.iitm.roster.constraints.Severity;
import com.iitm.roster.constraints.WeeklyShiftBlockConstraint;
import com.iitm.roster.domain.Assignment;
import com.iitm.roster.domain.DayOff;
import com.iitm.roster.domain.HospitalCalendar;
import com.iitm.roster.domain.Leave;
import com.iitm.roster.domain.Post;
import com.iitm.roster.domain.Roster;
import com.iitm.roster.domain.RosterStatus;
import com.iitm.roster.domain.ShiftSlot;
import com.iitm.roster.domain.Staff;
import com.iitm.roster.domain.StaffRole;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** A synthetic October 2026 shaped like the CMO's sheet: 11 doctors, two holidays, one short and one long leave. */
class HospitalRosterGeneratorTest {

	private static final YearMonth OCTOBER = YearMonth.of(2026, 10);
	private static final HospitalCalendar CALENDAR = new HospitalCalendar(Set.of(LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 20)));

	private static final HospitalRosterGenerator GENERATOR = new HospitalRosterGenerator(SHIFT_I, SHIFT_II, NIGHT);
	private static final List<Staff> DOCTORS = doctors(11);

	/** Generated once: a month takes a few seconds. */
	private static Roster october;

	private static List<Staff> doctors(int count) {
		List<Staff> doctors = new ArrayList<>();
		for (int i = 1; i <= count; i++) {
			doctors.add(new Staff(String.format("D%02d", i), "Test Doctor " + i, StaffRole.DOCTOR));
		}
		return doctors;
	}

	private static MonthInput october(List<Staff> doctors, List<Leave> leaves) {
		return new MonthInput(OCTOBER, doctors, doctors.get(0), leaves, CALENDAR, 4, 2);
	}

	private static List<Leave> leaves(List<Staff> doctors) {
		return List.of(
				new Leave(doctors.get(2), LocalDate.of(2026, 10, 7), LocalDate.of(2026, 10, 9)),
				new Leave(doctors.get(4), LocalDate.of(2026, 10, 22), LocalDate.of(2026, 11, 3)));
	}

	private static synchronized Roster generatedOctober() {
		if (october == null) {
			GenerationResult result = GENERATOR.generate(october(DOCTORS, leaves(DOCTORS)));
			assertThat(result).isInstanceOf(GenerationResult.Ok.class);
			october = ((GenerationResult.Ok) result).roster();
		}
		return october;
	}

	@Test
	void octoberHasNoHardViolations() {
		Roster roster = generatedOctober();

		for (Constraint constraint : GENERATOR.constraints(october(DOCTORS, leaves(DOCTORS)))) {
			if (constraint.severity() == Severity.HARD) {
				assertThat(constraint.check(roster)).as(constraint.id()).isEmpty();
			}
		}
	}

	@Test
	void everyDayHasANightAndTheRightNumbersOnEachShift() {
		Roster roster = generatedOctober();

		for (LocalDate day : roster.days()) {
			int needed = CALENDAR.isWeekendOrHoliday(day) ? 2 : 4;
			assertThat(roster.assignmentsOn(new ShiftSlot(day, NIGHT))).as("night %s", day).hasSize(1);
			assertThat(roster.assignmentsOn(new ShiftSlot(day, SHIFT_I))).as("shift I %s", day).hasSize(needed);
			assertThat(roster.assignmentsOn(new ShiftSlot(day, SHIFT_II))).as("shift II %s", day).hasSize(needed);
		}
	}

	@Test
	void nightsAreSharedAndNobodyOnLongLeaveIsRostered() {
		List<Staff> doctors = DOCTORS;
		Roster roster = generatedOctober();

		for (Staff doctor : doctors) {
			long nights = roster.assignmentsFor(doctor).stream().filter(a -> a.slot().shift().equals(NIGHT)).count();
			assertThat(nights).as(doctor.id()).isBetween(2L, 4L);
		}
		assertThat(roster.assignmentsFor(doctors.get(4))).noneMatch(a -> !a.slot().date().isBefore(LocalDate.of(2026, 10, 22)));
	}

	@Test
	void weeklyOffsAreMostlyOnWeekends() {
		Roster roster = generatedOctober();

		List<DayOff> offs = roster.weeklyOffs();
		long weekend = offs.stream().filter(o -> o.date().getDayOfWeek() == DayOfWeek.SATURDAY
				|| o.date().getDayOfWeek() == DayOfWeek.SUNDAY).count();
		assertThat(offs).hasSizeGreaterThanOrEqualTo(50);
		assertThat(weekend * 10).isGreaterThanOrEqualTo(offs.size() * 8L);
	}

	@Test
	void eachDayShiftHasOneWardAndOneCasualtyDoctor() {
		Roster roster = generatedOctober();

		for (LocalDate day : roster.days()) {
			for (ShiftSlot slot : List.of(new ShiftSlot(day, SHIFT_I), new ShiftSlot(day, SHIFT_II))) {
				List<Post> posts = roster.assignmentsOn(slot).stream().map(Assignment::post).toList();
				assertThat(posts).as("%s", slot).containsOnlyOnce(Post.WARD, Post.CASUALTY);
			}
		}
	}

	/** The CMO's rule: when a shift is short, a doctor is pulled from the other block instead of leaving it empty. */
	@Test
	void aShortShiftIsFilledFromTheOtherBlock() {
		List<Staff> pair = DOCTORS.subList(0, 2);
		List<Assignment> afternoons = new ArrayList<>();
		for (int day = 5; day <= 8; day++) {
			for (Staff doctor : pair) {
				afternoons.add(new Assignment(new ShiftSlot(OCTOBER.atDay(day), SHIFT_II), doctor));
			}
		}
		ShiftSlot fridayMorning = new ShiftSlot(OCTOBER.atDay(9), SHIFT_I);
		List<Constraint> block = GENERATOR.constraints(october(DOCTORS, List.of())).stream()
				.filter(c -> WeeklyShiftBlockConstraint.ID.equals(c.id())).toList();

		GenerationResult result = new GreedyRosterGenerator().generate(new GenerationRequest(
				new Roster(OCTOBER, RosterStatus.DRAFT, afternoons), pair,
				List.of(new OpenSlot(fridayMorning, StaffRole.DOCTOR, Post.GENERAL)), block));

		assertThat(result).isInstanceOf(GenerationResult.Ok.class);
		assertThat(((GenerationResult.Ok) result).roster().assignmentsOn(fridayMorning)).hasSize(1);
	}

	@Test
	void tooFewDoctorsIsInfeasibleNotAnError() {
		List<Staff> doctors = doctors(6);

		GenerationResult result = GENERATOR.generate(october(doctors, List.of()));

		assertThat(result).isInstanceOf(GenerationResult.Infeasible.class);
		assertThat(((GenerationResult.Infeasible) result).unfilled()).isNotEmpty();
	}

}
