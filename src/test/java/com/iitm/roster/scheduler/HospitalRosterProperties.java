package com.iitm.roster.scheduler;

import static com.iitm.roster.domain.StandardShifts.NIGHT;
import static com.iitm.roster.domain.StandardShifts.SHIFT_I;
import static com.iitm.roster.domain.StandardShifts.SHIFT_II;
import static org.assertj.core.api.Assertions.assertThat;

import com.iitm.roster.constraints.Constraint;
import com.iitm.roster.constraints.Severity;
import com.iitm.roster.constraints.WeeklyOffConstraint;
import com.iitm.roster.domain.Assignment;
import com.iitm.roster.domain.HospitalCalendar;
import com.iitm.roster.domain.Leave;
import com.iitm.roster.domain.Post;
import com.iitm.roster.domain.DayOff;
import com.iitm.roster.domain.Roster;
import com.iitm.roster.domain.ShiftSlot;
import com.iitm.roster.domain.Staff;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.jqwik.api.Example;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;

/**
 * The hospital rules every generated roster keeps, for random months: any size of doctor list,
 * first night doctor, leave (short and long), holidays and night before the month. They hold for a
 * partial (infeasible) roster too: a slot the generator cannot fill is left empty, never filled
 * against a rule. Each try generates a whole month, so there are fewer tries than usual.
 */
class HospitalRosterProperties {

	private final HospitalRosterGenerator generator = new HospitalRosterGenerator(SHIFT_I, SHIFT_II, NIGHT);

	@Property(tries = 40)
	void generatedRostersKeepTheHospitalsHardRules(@ForAll @IntRange(min = 0, max = 35) int monthOffset,
			@ForAll @IntRange(min = 6, max = 14) int doctorCount, @ForAll @IntRange(min = 0, max = 13) int firstIndex,
			@ForAll @Size(max = 4) List<@IntRange(min = 0, max = 999) Integer> leaveSeeds,
			@ForAll @Size(max = 3) List<@IntRange(min = 1, max = 28) Integer> holidayDays,
			@ForAll @IntRange(min = -1, max = 13) int nightBeforeIndex) {
		MonthInput input = input(monthOffset, doctorCount, firstIndex, leaveSeeds, holidayDays, nightBeforeIndex);

		GenerationResult result = generator.generate(input);
		Roster roster = roster(result);

		for (Assignment assignment : roster.assignments()) {
			LocalDate day = assignment.slot().date();
			Staff doctor = assignment.staff();
			assertThat(input.leaves()).as("%s on leave on %s", doctor.id(), day)
					.noneMatch(leave -> leave.staff().equals(doctor) && leave.covers(day));
			assertThat(nightDoctors(roster, input, day.minusDays(1))).as("%s works %s, the day after a night", doctor.id(), day)
					.doesNotContain(doctor);
		}
		for (LocalDate day : roster.days()) {
			for (Assignment night : roster.assignmentsOn(new ShiftSlot(day, NIGHT))) {
				assertThat(roster.assignmentsOn(new ShiftSlot(day, SHIFT_I)))
						.as("night doctor %s is Shift I ward on %s", night.staff().id(), day)
						.contains(new Assignment(new ShiftSlot(day, SHIFT_I), night.staff(), Post.WARD));
			}
		}
		if (result instanceof GenerationResult.Ok) {
			// Ok means every slot was filled without adding a HARD violation. A weekly off with no
			// room is owed to next month, which is not a violation.
			for (Constraint constraint : generator.constraints(input)) {
				if (constraint.severity() == Severity.HARD) {
					assertThat(constraint.check(roster)).as(constraint.id()).isEmpty();
				}
			}
		}
	}

	@Example
	void anOwedOffIsNotGivenOnThePostDutyOffAfterLastMonthsFinalNight() {
		YearMonth january = YearMonth.of(2027, 1);
		MonthInput plain = input(0, 11, 1, List.of(), List.of(), -1);
		Staff owed = plain.doctors().get(0);
		MonthInput input = new MonthInput(january, plain.doctors(), plain.doctors().get(1), List.of(), HospitalCalendar.noHolidays(),
				MonthInput.WEEKDAY_PER_SHIFT, MonthInput.WEEKEND_PER_SHIFT,
				List.of(new Assignment(new ShiftSlot(january.atDay(1).minusDays(1), NIGHT), owed)), List.of(owed));

		Roster roster = roster(generator.generate(input));

		assertThat(roster.weeklyOffs()).filteredOn(off -> off.staff().equals(owed) && off.date().isBefore(january.atDay(4)))
				.extracting(DayOff::date).containsExactly(january.atDay(2));
	}

	@Example
	void theGeneratorsWeeklyOffRuleSeesLastMonthsFinalNight() {
		YearMonth january = YearMonth.of(2027, 1);
		MonthInput plain = input(0, 11, 1, List.of(), List.of(), -1);
		Staff doctor = plain.doctors().get(0);
		MonthInput input = new MonthInput(january, plain.doctors(), plain.doctors().get(1), List.of(), HospitalCalendar.noHolidays(),
				MonthInput.WEEKDAY_PER_SHIFT, MonthInput.WEEKEND_PER_SHIFT,
				List.of(new Assignment(new ShiftSlot(january.atDay(1).minusDays(1), NIGHT), doctor)), List.of());
		Roster offOnThePostDutyDay = Roster.empty(january).withWeeklyOffs(List.of(new DayOff(doctor, january.atDay(1))));

		assertThat(generator.constraints(input)).filteredOn(c -> WeeklyOffConstraint.ID.equals(c.id())).singleElement()
				.satisfies(rule -> assertThat(rule.check(offOnThePostDutyDay)).hasSize(1));
	}

	@Property(tries = 15)
	void owedOffsAreGivenWithoutBreakingAHardRule(@ForAll @IntRange(min = 0, max = 35) int monthOffset,
			@ForAll @IntRange(min = 9, max = 14) int doctorCount, @ForAll @IntRange(min = 0, max = 13) int firstIndex,
			@ForAll @IntRange(min = 0, max = 4) int owedCount,
			@ForAll @Size(max = 4) List<@IntRange(min = 0, max = 999) Integer> leaveSeeds) {
		MonthInput plain = input(monthOffset, doctorCount, firstIndex, leaveSeeds, List.of(), -1);
		MonthInput input = plain.withOwedOffs(plain.doctors().subList(0, owedCount));

		GenerationResult result = generator.generate(input);
		Roster roster = roster(result);

		assertThat(roster.weeklyOffs()).doesNotHaveDuplicates();
		if (result instanceof GenerationResult.Ok) {
			for (Constraint constraint : generator.constraints(input)) {
				if (constraint.severity() == Severity.HARD) {
					assertThat(constraint.check(roster)).as(constraint.id()).isEmpty();
				}
			}
		}
	}

	@Property(tries = 10)
	void theSameInputAlwaysGivesTheSameRoster(@ForAll @IntRange(min = 0, max = 35) int monthOffset,
			@ForAll @IntRange(min = 6, max = 14) int doctorCount, @ForAll @IntRange(min = 0, max = 13) int firstIndex,
			@ForAll @Size(max = 4) List<@IntRange(min = 0, max = 999) Integer> leaveSeeds) {
		MonthInput input = input(monthOffset, doctorCount, firstIndex, leaveSeeds, List.of(), -1);

		assertThat(roster(generator.generate(input))).isEqualTo(roster(generator.generate(input)));
	}

	/** Leave seed: doctor = seed % count, first day = 1 + seed % 28, length = 1 + seed / 28 % 15 (short and long). */
	private static MonthInput input(int monthOffset, int doctorCount, int firstIndex, List<Integer> leaveSeeds,
			List<Integer> holidayDays, int nightBeforeIndex) {
		YearMonth month = YearMonth.of(2026, 1).plusMonths(monthOffset);
		List<Staff> doctors = NightRotationProperties.doctors(doctorCount);
		List<Leave> leaves = leaveSeeds.stream().map(seed -> {
			LocalDate start = month.atDay(1 + seed % 28);
			return new Leave(doctors.get(seed % doctorCount), start, start.plusDays(seed / 28 % 15));
		}).toList();
		Set<LocalDate> holidays = new HashSet<>();
		holidayDays.forEach(day -> holidays.add(month.atDay(day)));
		List<Assignment> carriedIn = nightBeforeIndex < 0 ? List.of()
				: List.of(new Assignment(new ShiftSlot(month.atDay(1).minusDays(1), NIGHT),
						doctors.get(nightBeforeIndex % doctorCount)));
		return new MonthInput(month, doctors, doctors.get(firstIndex % doctorCount), leaves, new HospitalCalendar(holidays),
				MonthInput.WEEKDAY_PER_SHIFT, MonthInput.WEEKEND_PER_SHIFT, carriedIn, List.of());
	}

	/** Who did the night of {@code day}, including a night carried in from the month before. */
	private static List<Staff> nightDoctors(Roster roster, MonthInput input, LocalDate day) {
		if (!YearMonth.from(day).equals(roster.month())) {
			return input.carriedIn().stream().filter(a -> a.slot().equals(new ShiftSlot(day, NIGHT)))
					.map(Assignment::staff).toList();
		}
		return roster.assignmentsOn(new ShiftSlot(day, NIGHT)).stream().map(Assignment::staff).toList();
	}

	private static Roster roster(GenerationResult result) {
		return switch (result) {
			case GenerationResult.Ok ok -> ok.roster();
			case GenerationResult.Infeasible infeasible -> infeasible.partial();
		};
	}

}
