package com.iitm.roster.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

// SpotBugs flags the with...() calls inside assertThatThrownBy lambdas: those calls are
// expected to throw, so there is no return value to use. The annotation has to sit on the
// class because the calls compile into synthetic lambda methods.
@SuppressFBWarnings(value = "RV_RETURN_VALUE_IGNORED_INFERRED", justification = "calls are expected to throw")
class RosterTest {

	private static final ShiftType MORNING = new ShiftType("M", LocalTime.of(8, 0), Duration.ofHours(6));
	private static final ShiftType EVENING = new ShiftType("E", LocalTime.of(14, 0), Duration.ofHours(6));
	private static final ShiftType NIGHT = new ShiftType("N", LocalTime.of(20, 0), Duration.ofHours(12));
	private static final ShiftType LONG_DAY = new ShiftType("L", LocalTime.of(8, 0), Duration.ofHours(12));

	private static final Staff DOC_A = new Staff("S01", "Test Doctor A", StaffRole.DOCTOR);
	private static final Staff DOC_B = new Staff("S02", "Test Doctor B", StaffRole.DOCTOR);

	private static final YearMonth JULY = YearMonth.of(2026, 7);

	private static Assignment on(int day, ShiftType shift, Staff staff) {
		return new Assignment(new ShiftSlot(JULY.atDay(day), shift), staff);
	}

	@Test
	void keepsAssignmentsThatDoNotOverlap() {
		Roster roster = new Roster(JULY, RosterStatus.DRAFT,
				List.of(on(1, MORNING, DOC_A), on(1, EVENING, DOC_A), on(1, MORNING, DOC_B)));

		assertThat(roster.assignmentsFor(DOC_A)).hasSize(2);
		assertThat(roster.assignmentsOn(new ShiftSlot(JULY.atDay(1), MORNING))).hasSize(2);
	}

	@Test
	void rejectsTheSamePersonTwiceInOneSlot() {
		assertThatThrownBy(() -> new Roster(JULY, RosterStatus.DRAFT,
				List.of(on(1, MORNING, DOC_A), on(1, MORNING, DOC_A))))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("double-booked");
	}

	@Test
	void rejectsOverlappingSlotsOfDifferentTypes() {
		// Long day 08:00-20:00 overlaps evening 14:00-20:00.
		assertThatThrownBy(() -> new Roster(JULY, RosterStatus.DRAFT,
				List.of(on(3, LONG_DAY, DOC_A), on(3, EVENING, DOC_A))))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsANightShiftOverlappingTheNextMorning() {
		// Night of the 3rd runs 20:00 to 08:00 on the 4th; a 07:00 shift on the 4th overlaps it.
		ShiftType early = new ShiftType("X", LocalTime.of(7, 0), Duration.ofHours(6));

		assertThatThrownBy(() -> new Roster(JULY, RosterStatus.DRAFT,
				List.of(on(3, NIGHT, DOC_A), on(4, early, DOC_A))))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void allowsAShiftStartingExactlyWhenTheLastOneEnds() {
		// Night ends 08:00 on the 4th, morning starts 08:00 on the 4th: back to back, not overlapping.
		Roster roster = new Roster(JULY, RosterStatus.DRAFT, List.of(on(3, NIGHT, DOC_A), on(4, MORNING, DOC_A)));

		assertThat(roster.assignments()).hasSize(2);
	}

	@Test
	void withAssignmentRejectsDoubleBookingAndLeavesTheOriginalUnchanged() {
		Roster roster = new Roster(JULY, RosterStatus.DRAFT, List.of(on(5, MORNING, DOC_A)));

		assertThatThrownBy(() -> roster.withAssignment(on(5, MORNING, DOC_A)))
				.isInstanceOf(IllegalArgumentException.class);
		assertThat(roster.assignments()).containsExactly(on(5, MORNING, DOC_A));
	}

	@Test
	void wouldDoubleBookMatchesWhatTheConstructorRejects() {
		Roster roster = new Roster(JULY, RosterStatus.DRAFT, List.of(on(3, NIGHT, DOC_A)));

		ShiftType late = new ShiftType("X", LocalTime.of(22, 0), Duration.ofHours(4));

		assertThat(roster.wouldDoubleBook(on(3, NIGHT, DOC_A))).isTrue();
		assertThat(roster.wouldDoubleBook(on(3, late, DOC_A))).isTrue();
		// Evening ends 20:00 as the night starts, and the night ends 08:00 as the morning starts.
		assertThat(roster.wouldDoubleBook(on(3, EVENING, DOC_A))).isFalse();
		assertThat(roster.wouldDoubleBook(on(4, MORNING, DOC_A))).isFalse();
		assertThat(roster.wouldDoubleBook(on(3, NIGHT, DOC_B))).isFalse();
	}

	@Test
	void withoutAssignmentRemovesItAndRejectsOneThatIsNotThere() {
		Roster roster = new Roster(JULY, RosterStatus.DRAFT, List.of(on(5, MORNING, DOC_A)));

		assertThat(roster.withoutAssignment(on(5, MORNING, DOC_A)).assignments()).isEmpty();
		assertThatThrownBy(() -> roster.withoutAssignment(on(5, MORNING, DOC_B)))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsASlotOutsideTheMonth() {
		Assignment augustFirst = new Assignment(new ShiftSlot(LocalDate.of(2026, 8, 1), MORNING), DOC_A);

		assertThatThrownBy(() -> new Roster(JULY, RosterStatus.DRAFT, List.of(augustFirst)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("outside");
	}

	@Test
	void decemberEndsOnTheLastDayOfTheYearAndItsLastNightEndsInJanuary() {
		YearMonth december = YearMonth.of(2026, 12);
		ShiftSlot lastNight = new ShiftSlot(LocalDate.of(2026, 12, 31), NIGHT);

		Roster roster = new Roster(december, RosterStatus.DRAFT, List.of(new Assignment(lastNight, DOC_A)));

		assertThat(roster.days()).hasSize(31);
		assertThat(roster.days().get(0)).isEqualTo(LocalDate.of(2026, 12, 1));
		assertThat(roster.days().get(30)).isEqualTo(LocalDate.of(2026, 12, 31));
		assertThat(lastNight.end().toLocalDate()).isEqualTo(LocalDate.of(2027, 1, 1));
	}

	@Test
	void februaryHas29DaysInALeapYearAnd28Otherwise() {
		assertThat(Roster.empty(YearMonth.of(2028, 2)).days()).hasSize(29);
		assertThat(Roster.empty(YearMonth.of(2027, 2)).days()).hasSize(28);
	}

	@Test
	void assignmentsCannotBeChangedFromOutside() {
		Roster roster = Roster.empty(JULY);

		assertThatThrownBy(() -> roster.assignments().add(on(1, MORNING, DOC_A)))
				.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void changingTheCallersListLaterDoesNotChangeTheRoster() {
		List<Assignment> input = new ArrayList<>(List.of(on(1, MORNING, DOC_A)));
		Roster roster = new Roster(JULY, RosterStatus.DRAFT, input);

		input.add(on(1, MORNING, DOC_A));

		assertThat(roster.assignments()).hasSize(1);
	}

	@Test
	void withStatusKeepsTheAssignments() {
		Roster draft = new Roster(JULY, RosterStatus.DRAFT, List.of(on(1, MORNING, DOC_A)));

		Roster published = draft.withStatus(RosterStatus.PUBLISHED);

		assertThat(published.status()).isEqualTo(RosterStatus.PUBLISHED);
		assertThat(published.assignments()).isEqualTo(draft.assignments());
	}

}
