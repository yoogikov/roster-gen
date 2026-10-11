package com.iitm.roster.constraints;

import static com.iitm.roster.constraints.Fixtures.DOC_A;
import static com.iitm.roster.constraints.Fixtures.DOC_B;
import static com.iitm.roster.constraints.Fixtures.MORNING;
import static com.iitm.roster.constraints.Fixtures.NIGHT;
import static com.iitm.roster.constraints.Fixtures.NURSE_A;
import static com.iitm.roster.constraints.Fixtures.on;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.iitm.roster.domain.Assignment;
import com.iitm.roster.domain.Roster;
import com.iitm.roster.domain.RosterStatus;
import com.iitm.roster.domain.StaffRole;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MinStaffingConstraintTest {

	private static final StaffingRequirement ONE_DOCTOR_PER_MORNING = new StaffingRequirement(MORNING, StaffRole.DOCTOR, 1);

	private static Roster everyMorning(YearMonth month, Assignment... extra) {
		List<Assignment> assignments = new ArrayList<>(List.of(extra));
		for (int day = 1; day <= month.lengthOfMonth(); day++) {
			assignments.add(on(month, day, MORNING, DOC_A));
		}
		return new Roster(month, RosterStatus.DRAFT, assignments);
	}

	@Test
	void fullyStaffedMonthHasNoViolations() {
		MinStaffingConstraint constraint = new MinStaffingConstraint(List.of(ONE_DOCTOR_PER_MORNING), Severity.HARD);

		assertThat(constraint.check(everyMorning(Fixtures.JULY))).isEmpty();
	}

	@Test
	void emptyRosterIsShortOnEveryDay() {
		MinStaffingConstraint constraint = new MinStaffingConstraint(List.of(ONE_DOCTOR_PER_MORNING), Severity.HARD);

		List<Violation> violations = constraint.check(Roster.empty(Fixtures.JULY));

		assertThat(violations).hasSize(31);
		assertThat(violations.get(0).date()).isEqualTo(LocalDate.of(2026, 7, 1));
		assertThat(violations.get(30).date()).isEqualTo(LocalDate.of(2026, 7, 31));
		assertThat(violations.get(0).message()).contains("needs at least 1 DOCTOR");
	}

	@Test
	void aNurseDoesNotCountTowardsADoctorRequirement() {
		MinStaffingConstraint constraint = new MinStaffingConstraint(List.of(ONE_DOCTOR_PER_MORNING), Severity.HARD);
		List<Assignment> assignments = new ArrayList<>();
		for (int day = 1; day <= 31; day++) {
			assignments.add(on(day, MORNING, day == 15 ? NURSE_A : DOC_A));
		}

		List<Violation> violations = constraint.check(new Roster(Fixtures.JULY, RosterStatus.DRAFT, assignments));

		assertThat(violations).extracting(Violation::date).containsExactly(LocalDate.of(2026, 7, 15));
	}

	@Test
	void countsEachShiftSeparately() {
		StaffingRequirement twoDoctorsAtNight = new StaffingRequirement(NIGHT, StaffRole.DOCTOR, 2);
		MinStaffingConstraint constraint = new MinStaffingConstraint(List.of(twoDoctorsAtNight), Severity.HARD);
		List<Assignment> assignments = new ArrayList<>();
		for (int day = 1; day <= 31; day++) {
			assignments.add(on(day, NIGHT, DOC_A));
			if (day != 3) {
				assignments.add(on(day, NIGHT, DOC_B));
			}
		}
		// Day-3 night has one doctor; working nights back to back is allowed by Roster.
		List<Violation> violations = constraint.check(new Roster(Fixtures.JULY, RosterStatus.DRAFT, assignments));

		assertThat(violations).extracting(Violation::date).containsExactly(LocalDate.of(2026, 7, 3));
	}

	@Test
	void coversEveryDayOfALeapFebruary() {
		MinStaffingConstraint constraint = new MinStaffingConstraint(List.of(ONE_DOCTOR_PER_MORNING), Severity.HARD);

		assertThat(constraint.check(Roster.empty(YearMonth.of(2028, 2)))).hasSize(29);
	}

	@Test
	void hardHasNoPenaltySoftCountsViolations() {
		Roster empty = Roster.empty(Fixtures.JULY);

		assertThat(new MinStaffingConstraint(List.of(ONE_DOCTOR_PER_MORNING), Severity.HARD).penalty(empty)).isZero();
		assertThat(new MinStaffingConstraint(List.of(ONE_DOCTOR_PER_MORNING), Severity.SOFT).penalty(empty)).isEqualTo(31);
	}

	@Test
	void noRequirementsMeansNoViolations() {
		assertThat(new MinStaffingConstraint(List.of(), Severity.HARD).check(Roster.empty(Fixtures.JULY))).isEmpty();
	}

	@Test
	void negativeMinimumIsRejected() {
		assertThatThrownBy(() -> new StaffingRequirement(MORNING, StaffRole.DOCTOR, -1))
				.isInstanceOf(IllegalArgumentException.class);
	}

}
