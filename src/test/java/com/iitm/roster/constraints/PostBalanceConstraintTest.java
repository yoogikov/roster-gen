package com.iitm.roster.constraints;

import static com.iitm.roster.domain.StandardShifts.SHIFT_I;
import static com.iitm.roster.domain.StandardShifts.SHIFT_II;
import static org.assertj.core.api.Assertions.assertThat;

import com.iitm.roster.domain.Assignment;
import com.iitm.roster.domain.Leave;
import com.iitm.roster.domain.Post;
import com.iitm.roster.domain.Roster;
import com.iitm.roster.domain.RosterStatus;
import com.iitm.roster.domain.ShiftSlot;
import com.iitm.roster.domain.Staff;
import com.iitm.roster.domain.StaffRole;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PostBalanceConstraintTest {

	private static final YearMonth JUNE = YearMonth.of(2026, 6);
	private static final Staff D1 = new Staff("D01", "Test Doctor 1", StaffRole.DOCTOR);
	private static final Staff D2 = new Staff("D02", "Test Doctor 2", StaffRole.DOCTOR);
	private static final Staff D3 = new Staff("D03", "Test Doctor 3", StaffRole.DOCTOR);
	private static final List<PostBalanceConstraint.Duty> CASUALTY_I = List.of(new PostBalanceConstraint.Duty(SHIFT_I, Post.CASUALTY));

	private final PostBalanceConstraint constraint = new PostBalanceConstraint(CASUALTY_I, List.of(D1, D2), List.of(), Severity.SOFT);

	/** Doctor at index i takes the casualty duty on as many days as {@code counts[i]}, from the 1st on. */
	private static Roster casualty(List<Staff> doctors, int... counts) {
		List<Assignment> work = new ArrayList<>();
		int day = 1;
		for (int i = 0; i < counts.length; i++) {
			for (int n = 0; n < counts[i]; n++) {
				work.add(new Assignment(new ShiftSlot(JUNE.atDay(day++), SHIFT_I), doctors.get(i), Post.CASUALTY));
			}
		}
		return new Roster(JUNE, RosterStatus.DRAFT, work);
	}

	@Test
	void anEvenSplitIsFine() {
		assertThat(constraint.check(casualty(List.of(D1, D2), 5, 5))).isEmpty();
	}

	@Test
	void anOddTotalMayDifferByOne() {
		assertThat(constraint.check(casualty(List.of(D1, D2), 6, 5))).isEmpty();
	}

	@Test
	void everyDutyAboveOrBelowTheShareCounts() {
		List<Violation> violations = constraint.check(casualty(List.of(D1, D2), 8, 2));

		assertThat(violations).hasSize(6);
		assertThat(violations.get(0).message())
				.isEqualTo("Test Doctor 1 has 8 casualty duties on shift I; a fair share is 5 to 5 (1)");
		assertThat(constraint.penalty(casualty(List.of(D1, D2), 8, 2))).isEqualTo(6);
	}

	@Test
	void aDoctorOnNoDutyAtAllStillCounts() {
		assertThat(constraint.check(casualty(List.of(D1), 4))).hasSize(4);
	}

	@Test
	void theShareShrinksWithDaysOnLeave() {
		// June has 30 days. D2 is away for 20 of them: shares of 9 duties are 6 3/4 and 2 1/4.
		Leave leave = new Leave(D2, JUNE.atDay(1), JUNE.atDay(20));
		PostBalanceConstraint withLeave = new PostBalanceConstraint(CASUALTY_I, List.of(D1, D2), List.of(leave), Severity.SOFT);

		assertThat(withLeave.check(casualty(List.of(D1, D2), 7, 2))).isEmpty();
		assertThat(withLeave.check(casualty(List.of(D1, D2), 5, 4))).isNotEmpty();
	}

	@Test
	void onlyTheDutiesListedAreCounted() {
		Roster otherDuty = new Roster(JUNE, RosterStatus.DRAFT, List.of(
				new Assignment(new ShiftSlot(JUNE.atDay(1), SHIFT_II), D1, Post.CASUALTY),
				new Assignment(new ShiftSlot(JUNE.atDay(2), SHIFT_I), D1, Post.WARD)));

		assertThat(constraint.check(otherDuty)).isEmpty();
	}

	@Test
	void someoneNotOnTheListIsIgnored() {
		assertThat(constraint.check(casualty(List.of(D3, D1, D2), 9, 1, 1))).isEmpty();
	}

	@Test
	void hardSeverityHasNoPenalty() {
		PostBalanceConstraint hard = new PostBalanceConstraint(CASUALTY_I, List.of(D1, D2), List.of(), Severity.HARD);

		assertThat(hard.penalty(casualty(List.of(D1, D2), 8, 2))).isZero();
	}

}
