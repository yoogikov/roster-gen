package com.iitm.roster.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * {@link Roster} refuses double-booking with a fast check (one sorted pass per person). These
 * properties compare it with the plain definition: a roster is refused exactly when some pair of
 * one person's assignments overlaps, and the message names the first such pair.
 */
class DoubleBookingProperties {

	private static final YearMonth MONTH = YearMonth.of(2026, 10);
	private static final List<ShiftType> SHIFTS = List.of(StandardShifts.SHIFT_I, StandardShifts.SHIFT_II, StandardShifts.NIGHT);

	@Property(tries = 500)
	void refusedExactlyWhenSomePairOfOnePersonsShiftsOverlaps(@ForAll("assignments") List<Assignment> assignments) {
		List<Assignment> first = firstOverlappingPair(assignments);

		if (first.isEmpty()) {
			assertThat(new Roster(MONTH, RosterStatus.DRAFT, assignments).assignments()).isEqualTo(assignments);
		} else {
			assertThatThrownBy(() -> new Roster(MONTH, RosterStatus.DRAFT, assignments))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessage("double-booked: " + first.get(0) + " and " + first.get(1));
		}
	}

	/** The plain definition: every pair, in order; the first that overlaps, or empty if none does. */
	private static List<Assignment> firstOverlappingPair(List<Assignment> assignments) {
		for (int i = 0; i < assignments.size(); i++) {
			for (int j = i + 1; j < assignments.size(); j++) {
				Assignment a = assignments.get(i);
				Assignment b = assignments.get(j);
				if (a.staff().equals(b.staff()) && a.slot().overlaps(b.slot())) {
					return List.of(a, b);
				}
			}
		}
		return List.of();
	}

	/** A few doctors on a few days, so overlaps (same day I and II, night then next morning) are common. */
	@Provide
	Arbitrary<List<Assignment>> assignments() {
		Arbitrary<Assignment> one = Combinators.combine(
				Arbitraries.integers().between(1, 3),
				Arbitraries.integers().between(1, 5),
				Arbitraries.of(SHIFTS))
				.as((doctor, day, shift) -> new Assignment(new ShiftSlot(MONTH.atDay(day), shift),
						new Staff("D0" + doctor, "Test Doctor " + doctor, StaffRole.DOCTOR)));
		return one.list().ofMaxSize(12).map(list -> new ArrayList<>(list.stream().distinct().toList()));
	}

}
