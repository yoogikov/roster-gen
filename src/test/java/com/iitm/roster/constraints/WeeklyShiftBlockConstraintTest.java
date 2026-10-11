package com.iitm.roster.constraints;

import static com.iitm.roster.constraints.HospitalFixtures.D1;
import static com.iitm.roster.constraints.HospitalFixtures.OCTOBER;
import static com.iitm.roster.constraints.HospitalFixtures.at;
import static com.iitm.roster.domain.StandardShifts.SHIFT_I;
import static com.iitm.roster.domain.StandardShifts.SHIFT_II;
import static org.assertj.core.api.Assertions.assertThat;

import com.iitm.roster.domain.Assignment;
import com.iitm.roster.domain.Post;
import com.iitm.roster.domain.Roster;
import com.iitm.roster.domain.RosterStatus;
import com.iitm.roster.domain.ShiftType;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class WeeklyShiftBlockConstraintTest {

	private final WeeklyShiftBlockConstraint constraint = new WeeklyShiftBlockConstraint(SHIFT_I, SHIFT_II, Severity.SOFT);

	/** Monday 5 Oct to Friday 9 Oct, then Monday 12 to Friday 16. */
	private static Roster twoWeeks(ShiftType first, ShiftType second) {
		List<Assignment> work = new ArrayList<>();
		for (int day = 5; day <= 9; day++) {
			work.add(at(day, first, D1, Post.GENERAL));
			work.add(at(day + 7, second, D1, Post.GENERAL));
		}
		return new Roster(OCTOBER, RosterStatus.DRAFT, work);
	}

	@Test
	void morningsThenAfternoonsIsPerfect() {
		assertThat(constraint.check(twoWeeks(SHIFT_I, SHIFT_II))).isEmpty();
	}

	@Test
	void theSameBlockTwoWeeksRunningCostsOnePoint() {
		assertThat(constraint.penalty(twoWeeks(SHIFT_I, SHIFT_I))).isEqualTo(1);
	}

	@Test
	void eachShiftOnTheMinorityBlockCostsOnePoint() {
		Roster roster = new Roster(OCTOBER, RosterStatus.DRAFT, List.of(
				at(5, SHIFT_I, D1, Post.GENERAL), at(6, SHIFT_I, D1, Post.GENERAL), at(7, SHIFT_II, D1, Post.GENERAL)));

		assertThat(constraint.penalty(roster)).isEqualTo(1);
	}

	@Test
	void hardVersionHasNoPenalty() {
		assertThat(new WeeklyShiftBlockConstraint(SHIFT_I, SHIFT_II, Severity.HARD).penalty(twoWeeks(SHIFT_I, SHIFT_I))).isZero();
	}

}
