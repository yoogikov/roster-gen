package com.iitm.roster.scheduler;

import static com.iitm.roster.domain.StandardShifts.NIGHT;
import static com.iitm.roster.domain.StandardShifts.SHIFT_I;
import static com.iitm.roster.domain.StandardShifts.SHIFT_II;
import static org.assertj.core.api.Assertions.assertThat;

import com.iitm.roster.constraints.Constraint;
import com.iitm.roster.constraints.PostBalanceConstraint;
import com.iitm.roster.constraints.Severity;
import com.iitm.roster.constraints.Violation;
import com.iitm.roster.domain.Assignment;
import com.iitm.roster.domain.Post;
import com.iitm.roster.domain.Roster;
import com.iitm.roster.domain.RosterStatus;
import com.iitm.roster.domain.ShiftSlot;
import com.iitm.roster.domain.ShiftType;
import com.iitm.roster.domain.Staff;
import com.iitm.roster.domain.StaffRole;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class SoftRepairTest {

	private static final YearMonth JUNE = YearMonth.of(2026, 6);
	private static final Staff D1 = new Staff("D01", "Test Doctor 1", StaffRole.DOCTOR);
	private static final Staff D2 = new Staff("D02", "Test Doctor 2", StaffRole.DOCTOR);
	private static final Constraint BALANCE = new PostBalanceConstraint(
			List.of(new PostBalanceConstraint.Duty(SHIFT_I, Post.CASUALTY)), List.of(D1, D2), List.of(), Severity.SOFT);

	/** One violation for each assignment {@code bad} matches. */
	private static Constraint rule(Severity severity, Predicate<Assignment> bad) {
		return new Constraint() {
			@Override
			public String id() {
				return "test-rule";
			}

			@Override
			public Severity severity() {
				return severity;
			}

			@Override
			public List<Violation> check(Roster roster) {
				return roster.assignments().stream().filter(bad)
						.map(a -> new Violation("test-rule", severity, a.slot().date(), a.toString())).toList();
			}

			@Override
			public int penalty(Roster roster) {
				return severity == Severity.SOFT ? check(roster).size() : 0;
			}
		};
	}

	private static Assignment on(int day, ShiftType shift, Staff doctor, Post post) {
		return new Assignment(new ShiftSlot(JUNE.atDay(day), shift), doctor, post);
	}

	private static Roster roster(List<Assignment> work) {
		return new Roster(JUNE, RosterStatus.DRAFT, work);
	}

	/** D1 has the casualty duty and D2 general duty on Shift I, on each of the first {@code days} days. */
	private static List<Assignment> lopsided(int days) {
		List<Assignment> work = new ArrayList<>();
		for (int day = 1; day <= days; day++) {
			work.add(on(day, SHIFT_I, D1, Post.CASUALTY));
			work.add(on(day, SHIFT_I, D2, Post.GENERAL));
		}
		return work;
	}

	private static long casualties(Roster roster, Staff doctor) {
		return roster.assignmentsFor(doctor).stream().filter(a -> a.post() == Post.CASUALTY).count();
	}

	@Test
	void exchangesDutiesUntilTheyAreShared() {
		Roster repaired = SoftRepair.improve(roster(lopsided(6)), List.of(), List.of(BALANCE));

		assertThat(casualties(repaired, D1)).isEqualTo(3);
		assertThat(casualties(repaired, D2)).isEqualTo(3);
	}

	@Test
	void everyDoctorKeepsTheirShiftsOnTheSameDays() {
		Roster start = roster(lopsided(6));

		Roster repaired = SoftRepair.improve(start, List.of(), List.of(BALANCE));

		for (Staff doctor : List.of(D1, D2)) {
			List<LocalDate> before = start.assignmentsFor(doctor).stream().map(a -> a.slot().date()).sorted().toList();
			assertThat(repaired.assignmentsFor(doctor).stream().map(a -> a.slot().date()).sorted().toList()).isEqualTo(before);
		}
	}

	@Test
	void anExchangeThatWouldAddAHardViolationIsNotMade() {
		Constraint d2NoCasualtyOnTheFirstThree = rule(Severity.HARD,
				a -> a.staff().equals(D2) && a.post() == Post.CASUALTY && a.slot().date().getDayOfMonth() <= 3);

		Roster repaired = SoftRepair.improve(roster(lopsided(6)), List.of(d2NoCasualtyOnTheFirstThree), List.of(BALANCE));

		assertThat(d2NoCasualtyOnTheFirstThree.check(repaired)).isEmpty();
		assertThat(casualties(repaired, D2)).isEqualTo(3);
	}

	@Test
	void aHardViolationAlreadyThereDoesNotStopRepair() {
		Constraint alreadyBroken = rule(Severity.HARD, a -> a.slot().date().getDayOfMonth() == 30);
		List<Assignment> work = new ArrayList<>(lopsided(4));
		work.add(on(30, NIGHT, D1, Post.GENERAL));

		Roster repaired = SoftRepair.improve(roster(work), List.of(alreadyBroken), List.of(BALANCE));

		assertThat(casualties(repaired, D2)).isEqualTo(2);
	}

	@Test
	void exchangesAcrossShiftsOfTheSameDay() {
		Roster start = roster(List.of(on(1, SHIFT_II, D1, Post.WARD), on(1, SHIFT_I, D2, Post.CASUALTY)));

		Roster repaired = SoftRepair.improve(start, List.of(), List.of(rule(Severity.SOFT, a -> a.staff().equals(D1) && a.slot().shift().equals(SHIFT_II))));

		assertThat(repaired.assignmentsFor(D1)).containsExactly(on(1, SHIFT_I, D1, Post.CASUALTY));
		assertThat(repaired.assignmentsFor(D2)).containsExactly(on(1, SHIFT_II, D2, Post.WARD));
	}

	@Test
	void neverExchangesBetweenDifferentDays() {
		Roster start = roster(List.of(on(1, SHIFT_I, D1, Post.CASUALTY), on(2, SHIFT_I, D2, Post.CASUALTY)));

		Roster repaired = SoftRepair.improve(start, List.of(), List.of(rule(Severity.SOFT, a -> a.staff().equals(D1) && a.slot().date().equals(JUNE.atDay(1)))));

		assertThat(repaired).isEqualTo(start);
	}

	@Test
	void anExchangeThatDoesNotLowerThePenaltyIsNotMade() {
		Roster start = roster(lopsided(6));

		assertThat(SoftRepair.improve(start, List.of(), List.of(rule(Severity.SOFT, a -> a.post() == Post.CASUALTY)))).isEqualTo(start);
	}

	@Test
	void anExchangeThatWouldDoubleBookIsNotMade() {
		// Only moving D1 to the night would help, but that leaves D2 on Shift I and Shift II, which overlap.
		Roster start = roster(List.of(on(1, SHIFT_I, D1, Post.CASUALTY), on(1, NIGHT, D2, Post.GENERAL), on(1, SHIFT_II, D2, Post.WARD)));

		Roster repaired = SoftRepair.improve(start, List.of(), List.of(rule(Severity.SOFT, a -> a.staff().equals(D1) && !a.slot().shift().equals(NIGHT))));

		assertThat(repaired).isEqualTo(start);
	}

}
