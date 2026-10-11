package com.iitm.roster.constraints;

import static com.iitm.roster.constraints.HospitalFixtures.D1;
import static com.iitm.roster.constraints.HospitalFixtures.OCTOBER;
import static com.iitm.roster.constraints.HospitalFixtures.at;
import static com.iitm.roster.constraints.HospitalFixtures.night;
import static com.iitm.roster.domain.StandardShifts.NIGHT;
import static com.iitm.roster.domain.StandardShifts.SHIFT_I;
import static org.assertj.core.api.Assertions.assertThat;

import com.iitm.roster.domain.Assignment;
import com.iitm.roster.domain.DayOff;
import com.iitm.roster.domain.Leave;
import com.iitm.roster.domain.Post;
import com.iitm.roster.domain.Roster;
import com.iitm.roster.domain.RosterStatus;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class WeeklyOffConstraintTest {

	private final WeeklyOffConstraint constraint = new WeeklyOffConstraint(NIGHT, List.of(), Severity.HARD);

	private static List<DayOff> offs(int... days) {
		List<DayOff> offs = new ArrayList<>();
		for (int day : days) {
			offs.add(new DayOff(D1, OCTOBER.atDay(day)));
		}
		return offs;
	}

	private static Roster worksFirst(List<DayOff> offs) {
		return new Roster(OCTOBER, RosterStatus.DRAFT, List.of(at(1, SHIFT_I, D1, Post.GENERAL)), offs);
	}

	@Test
	void countsWeeksWithAtLeastFourDaysInTheMonth() {
		// October 2026: Thu 1 - Sun 4, three full weeks, then Mon 26 - Sat 31.
		assertThat(WeeklyOffConstraint.countedWeeks(Roster.empty(OCTOBER)))
				.extracting(week -> week.get(0).getDayOfMonth()).containsExactly(1, 5, 12, 19, 26);
		// May 2026 starts on a Friday: a three-day week is left to April.
		assertThat(WeeklyOffConstraint.countedWeeks(Roster.empty(YearMonth.of(2026, 5))).get(0).get(0))
				.isEqualTo(LocalDate.of(2026, 5, 4));
		// November 2026 starts on a Sunday: that one-day week belongs to October's last week.
		assertThat(WeeklyOffConstraint.countedWeeks(Roster.empty(YearMonth.of(2026, 11))).get(0).get(0))
				.isEqualTo(LocalDate.of(2026, 11, 2));
	}

	@Test
	void oneOffInEveryCountedWeekIsFine() {
		assertThat(constraint.check(worksFirst(offs(4, 11, 18, 25, 31)))).isEmpty();
	}

	@Test
	void aWeekWithoutAnOffIsAViolation() {
		List<Violation> violations = constraint.check(worksFirst(offs(4, 11, 25, 31)));

		assertThat(violations).extracting(Violation::date).containsExactly(LocalDate.of(2026, 10, 12));
	}

	@Test
	void workingOnYourOffIsAViolation() {
		List<Assignment> work = List.of(at(4, SHIFT_I, D1, Post.GENERAL));

		assertThat(constraint.check(new Roster(OCTOBER, RosterStatus.DRAFT, work, offs(4, 11, 18, 25, 31))))
				.extracting(Violation::message).containsExactly("Test Doctor 1 is rostered on their weekly off");
	}

	@Test
	void theOffCannotBeThePostDutyOff() {
		List<Assignment> work = new ArrayList<>(night(3, D1));

		assertThat(constraint.check(new Roster(OCTOBER, RosterStatus.DRAFT, work, offs(4, 11, 18, 25, 31))))
				.extracting(Violation::message).containsExactly("Test Doctor 1's weekly off falls on their post-duty off");
	}

	@Test
	void someoneOnLeaveForTheWholeWeekNeedsNoOff() {
		Leave leave = new Leave(D1, LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 18));
		WeeklyOffConstraint withLeave = new WeeklyOffConstraint(NIGHT, List.of(leave), Severity.HARD);

		assertThat(withLeave.check(worksFirst(offs(4, 25, 31, 11)))).isEmpty();
	}

}
