package com.iitm.roster.constraints;

import static com.iitm.roster.constraints.HospitalFixtures.D1;
import static com.iitm.roster.constraints.HospitalFixtures.D2;
import static com.iitm.roster.constraints.HospitalFixtures.OCTOBER;
import static com.iitm.roster.constraints.HospitalFixtures.at;
import static com.iitm.roster.constraints.HospitalFixtures.night;
import static com.iitm.roster.domain.StandardShifts.NIGHT;
import static com.iitm.roster.domain.StandardShifts.SHIFT_I;
import static org.assertj.core.api.Assertions.assertThat;

import com.iitm.roster.domain.Post;
import com.iitm.roster.domain.Roster;
import com.iitm.roster.domain.RosterStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

class NightIncludesMorningWardConstraintTest {

	private final NightIncludesMorningWardConstraint constraint = new NightIncludesMorningWardConstraint(SHIFT_I, NIGHT, Severity.HARD);

	@Test
	void nightDoctorAsMorningWardIsFine() {
		assertThat(constraint.check(new Roster(OCTOBER, RosterStatus.DRAFT, night(5, D1)))).isEmpty();
	}

	@Test
	void nightWithoutTheMorningWardDutyIsAViolation() {
		Roster roster = new Roster(OCTOBER, RosterStatus.DRAFT, List.of(at(5, NIGHT, D1, Post.GENERAL)));

		assertThat(constraint.check(roster)).extracting(Violation::message)
				.containsExactly("Test Doctor 1 is on nights but not ward doctor on shift I");
	}

	@Test
	void someoneElseAsMorningWardOnANightDayIsAViolation() {
		Roster roster = new Roster(OCTOBER, RosterStatus.DRAFT, List.of(at(5, NIGHT, D1, Post.GENERAL), at(5, SHIFT_I, D2, Post.WARD)));

		assertThat(constraint.check(roster)).hasSize(2);
	}

	@Test
	void morningWardWithNoNightPlannedIsNotThisRulesProblem() {
		Roster roster = new Roster(OCTOBER, RosterStatus.DRAFT, List.of(at(5, SHIFT_I, D2, Post.WARD)));

		assertThat(constraint.check(roster)).isEmpty();
	}

}
