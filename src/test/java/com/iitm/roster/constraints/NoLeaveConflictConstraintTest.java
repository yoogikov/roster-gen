package com.iitm.roster.constraints;

import static com.iitm.roster.constraints.Fixtures.DOC_A;
import static com.iitm.roster.constraints.Fixtures.DOC_B;
import static com.iitm.roster.constraints.Fixtures.MORNING;
import static com.iitm.roster.constraints.Fixtures.NIGHT;
import static com.iitm.roster.constraints.Fixtures.july;
import static com.iitm.roster.constraints.Fixtures.on;
import static org.assertj.core.api.Assertions.assertThat;

import com.iitm.roster.domain.Leave;
import com.iitm.roster.domain.Roster;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class NoLeaveConflictConstraintTest {

	private static final Leave DOC_A_10_TO_12 = new Leave(DOC_A, LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 12));

	private final NoLeaveConflictConstraint constraint = new NoLeaveConflictConstraint(List.of(DOC_A_10_TO_12), Severity.HARD);

	@Test
	void shiftsOutsideTheLeaveAreFine() {
		assertThat(constraint.check(july(on(9, MORNING, DOC_A), on(13, MORNING, DOC_A)))).isEmpty();
	}

	@Test
	void shiftsOnTheFirstAndLastLeaveDayAreViolations() {
		List<Violation> violations = constraint.check(july(on(10, MORNING, DOC_A), on(12, MORNING, DOC_A)));

		assertThat(violations).extracting(Violation::date)
				.containsExactly(LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 12));
		assertThat(violations.get(0).message()).contains("Test Doctor A");
	}

	@Test
	void someoneElsesLeaveDoesNotMatter() {
		assertThat(constraint.check(july(on(11, MORNING, DOC_B)))).isEmpty();
	}

	@Test
	void aNightEndingOnTheFirstLeaveMorningIsAllowed() {
		// Night of the 9th ends 08:00 on the 10th; it starts before the leave.
		assertThat(constraint.check(july(on(9, NIGHT, DOC_A)))).isEmpty();
	}

	@Test
	void noLeavesMeansNoViolations() {
		Roster roster = july(on(11, MORNING, DOC_A));

		assertThat(new NoLeaveConflictConstraint(List.of(), Severity.HARD).check(roster)).isEmpty();
	}

	@Test
	void softVersionCountsOnePointPerConflict() {
		Roster roster = july(on(10, MORNING, DOC_A), on(11, MORNING, DOC_A));

		assertThat(constraint.penalty(roster)).isZero();
		assertThat(new NoLeaveConflictConstraint(List.of(DOC_A_10_TO_12), Severity.SOFT).penalty(roster)).isEqualTo(2);
	}

}
