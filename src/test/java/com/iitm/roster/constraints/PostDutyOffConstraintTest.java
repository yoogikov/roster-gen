package com.iitm.roster.constraints;

import static com.iitm.roster.constraints.HospitalFixtures.D1;
import static com.iitm.roster.constraints.HospitalFixtures.D2;
import static com.iitm.roster.constraints.HospitalFixtures.OCTOBER;
import static com.iitm.roster.constraints.HospitalFixtures.at;
import static com.iitm.roster.constraints.HospitalFixtures.night;
import static com.iitm.roster.domain.StandardShifts.NIGHT;
import static com.iitm.roster.domain.StandardShifts.SHIFT_I;
import static com.iitm.roster.domain.StandardShifts.SHIFT_II;
import static org.assertj.core.api.Assertions.assertThat;

import com.iitm.roster.domain.Assignment;
import com.iitm.roster.domain.Post;
import com.iitm.roster.domain.Roster;
import com.iitm.roster.domain.RosterStatus;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PostDutyOffConstraintTest {

	private final PostDutyOffConstraint constraint = new PostDutyOffConstraint(NIGHT, Severity.HARD);

	private static Roster with(List<Assignment> base, Assignment... more) {
		List<Assignment> all = new ArrayList<>(base);
		all.addAll(List.of(more));
		return new Roster(OCTOBER, RosterStatus.DRAFT, all);
	}

	@Test
	void anAfternoonTheDayAfterANightIsAViolation() {
		// The night ends 08:00, so an afternoon at 13:00 does not overlap; only this rule stops it.
		List<Violation> violations = constraint.check(with(night(5, D1), at(6, SHIFT_II, D1, Post.GENERAL)));

		assertThat(violations).extracting(Violation::date).containsExactly(LocalDate.of(2026, 10, 6));
	}

	@Test
	void workingTheDayAfterThePostDutyOffIsFine() {
		assertThat(constraint.check(with(night(5, D1), at(7, SHIFT_I, D1, Post.GENERAL)))).isEmpty();
	}

	@Test
	void someoneElseWorkingThatDayIsFine() {
		assertThat(constraint.check(with(night(5, D1), at(6, SHIFT_I, D2, Post.GENERAL)))).isEmpty();
	}

	@Test
	void aNightOnTheLastDayHasItsOffDayInTheNextMonth() {
		assertThat(constraint.check(with(night(31, D1)))).isEmpty();
	}

}
