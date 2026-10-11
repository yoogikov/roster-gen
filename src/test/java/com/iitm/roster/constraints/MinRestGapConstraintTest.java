package com.iitm.roster.constraints;

import static com.iitm.roster.constraints.Fixtures.DOC_A;
import static com.iitm.roster.constraints.Fixtures.DOC_B;
import static com.iitm.roster.constraints.Fixtures.EVENING;
import static com.iitm.roster.constraints.Fixtures.MORNING;
import static com.iitm.roster.constraints.Fixtures.NIGHT;
import static com.iitm.roster.constraints.Fixtures.july;
import static com.iitm.roster.constraints.Fixtures.on;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.iitm.roster.domain.Roster;
import com.iitm.roster.domain.RosterStatus;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

class MinRestGapConstraintTest {

	private final MinRestGapConstraint eightHours = new MinRestGapConstraint(Duration.ofHours(8), Severity.HARD);

	@Test
	void enoughRestBetweenShiftsIsFine() {
		// Morning ends 14:00 on the 1st; next morning starts 08:00 on the 2nd: 18 h.
		assertThat(eightHours.check(july(on(1, MORNING, DOC_A), on(2, MORNING, DOC_A)))).isEmpty();
	}

	@Test
	void backToBackShiftsOnTheSameDayAreAViolation() {
		// Morning ends 14:00, evening starts 14:00: 0 h rest.
		List<Violation> violations = eightHours.check(july(on(1, MORNING, DOC_A), on(1, EVENING, DOC_A)));

		assertThat(violations).hasSize(1);
		assertThat(violations.get(0).message()).contains("gets 0 h rest", "needs 8 h");
	}

	@Test
	void restIsMeasuredAcrossMidnightFromTheEndOfANight() {
		// Night of the 3rd ends 08:00 on the 4th; evening of the 4th starts 14:00: 6 h.
		List<Violation> violations = eightHours.check(july(on(3, NIGHT, DOC_A), on(4, EVENING, DOC_A)));

		assertThat(violations).extracting(Violation::date).containsExactly(LocalDate.of(2026, 7, 4));
	}

	@Test
	void exactlyTheMinimumIsAllowed() {
		// Evening ends 20:00 on the 5th; morning starts 08:00 on the 6th: 12 h.
		MinRestGapConstraint twelveHours = new MinRestGapConstraint(Duration.ofHours(12), Severity.HARD);

		assertThat(twelveHours.check(july(on(5, EVENING, DOC_A), on(6, MORNING, DOC_A)))).isEmpty();
	}

	@Test
	void eachPersonIsCheckedSeparatelyAndOrderOfInputDoesNotMatter() {
		Roster roster = july(on(2, MORNING, DOC_A), on(1, EVENING, DOC_B), on(1, MORNING, DOC_A), on(2, MORNING, DOC_B));

		assertThat(eightHours.check(roster)).isEmpty();
	}

	@Test
	void worksAcrossTheYearEnd() {
		YearMonth december = YearMonth.of(2026, 12);
		Roster roster = new Roster(december, RosterStatus.DRAFT,
				List.of(on(december, 30, NIGHT, DOC_A), on(december, 31, EVENING, DOC_A)));

		assertThat(eightHours.check(roster)).extracting(Violation::date).containsExactly(LocalDate.of(2026, 12, 31));
	}

	@Test
	void hardHasNoPenaltySoftCountsViolations() {
		Roster roster = july(on(1, MORNING, DOC_A), on(1, EVENING, DOC_A));

		assertThat(eightHours.penalty(roster)).isZero();
		assertThat(new MinRestGapConstraint(Duration.ofHours(8), Severity.SOFT).penalty(roster)).isEqualTo(1);
	}

	@Test
	void negativeRestIsRejected() {
		assertThatThrownBy(() -> new MinRestGapConstraint(Duration.ofHours(-1), Severity.HARD))
				.isInstanceOf(IllegalArgumentException.class);
	}

}
