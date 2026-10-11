package com.iitm.roster.constraints;

import static com.iitm.roster.constraints.HospitalFixtures.D1;
import static com.iitm.roster.constraints.HospitalFixtures.D2;
import static com.iitm.roster.constraints.HospitalFixtures.D3;
import static com.iitm.roster.constraints.HospitalFixtures.OCTOBER;
import static com.iitm.roster.constraints.HospitalFixtures.at;
import static com.iitm.roster.domain.StandardShifts.SHIFT_I;
import static org.assertj.core.api.Assertions.assertThat;

import com.iitm.roster.domain.Post;
import com.iitm.roster.domain.Roster;
import com.iitm.roster.domain.RosterStatus;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class PostCoverageConstraintTest {

	private final PostCoverageConstraint constraint = new PostCoverageConstraint(List.of(SHIFT_I), Severity.HARD);

	@Test
	void emptyMonthMissesAWardAndACasualtyDoctorEveryDay() {
		assertThat(constraint.check(Roster.empty(OCTOBER))).hasSize(31 * 2);
	}

	@Test
	void oneWardAndOneCasualtyDoctorSatisfyTheDay() {
		Roster roster = new Roster(OCTOBER, RosterStatus.DRAFT, List.of(
				at(1, SHIFT_I, D1, Post.WARD), at(1, SHIFT_I, D2, Post.CASUALTY), at(1, SHIFT_I, D3, Post.GENERAL)));

		assertThat(constraint.check(roster)).noneMatch(v -> v.date().equals(LocalDate.of(2026, 10, 1)));
	}

	@Test
	void twoWardDoctorsOnOneShiftIsAViolation() {
		Roster roster = new Roster(OCTOBER, RosterStatus.DRAFT, List.of(
				at(1, SHIFT_I, D1, Post.WARD), at(1, SHIFT_I, D2, Post.WARD), at(1, SHIFT_I, D3, Post.CASUALTY)));

		assertThat(constraint.check(roster)).filteredOn(v -> v.date().equals(LocalDate.of(2026, 10, 1)))
				.extracting(Violation::message).containsExactly("2 WARD doctors on shift I");
	}

	@Test
	void generalDutyDoesNotCountAsWardOrCasualty() {
		Roster roster = new Roster(OCTOBER, RosterStatus.DRAFT, List.of(at(1, SHIFT_I, D1, Post.GENERAL)));

		assertThat(constraint.check(roster)).filteredOn(v -> v.date().equals(LocalDate.of(2026, 10, 1))).hasSize(2);
	}

}
