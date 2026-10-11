package com.iitm.roster.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

class ShiftSlotTest {

	private static final ShiftType NIGHT = new ShiftType("N", LocalTime.of(20, 0), Duration.ofHours(12));

	@Test
	void startAndEndAreInIndianTime() {
		ShiftSlot slot = new ShiftSlot(LocalDate.of(2026, 7, 14), NIGHT);

		assertThat(slot.start()).isEqualTo(ZonedDateTime.of(2026, 7, 14, 20, 0, 0, 0, HospitalTime.ZONE));
		assertThat(slot.end()).isEqualTo(ZonedDateTime.of(2026, 7, 15, 8, 0, 0, 0, HospitalTime.ZONE));
		assertThat(slot.start().getOffset().getTotalSeconds()).isEqualTo(5 * 3600 + 30 * 60);
	}

	@Test
	void overlapIsSymmetric() {
		ShiftSlot night = new ShiftSlot(LocalDate.of(2026, 7, 14), NIGHT);
		ShiftSlot early = new ShiftSlot(LocalDate.of(2026, 7, 15),
				new ShiftType("X", LocalTime.of(7, 0), Duration.ofHours(1)));

		assertThat(night.overlaps(early)).isTrue();
		assertThat(early.overlaps(night)).isTrue();
	}

	@Test
	void shiftLengthMustBeBetweenZeroAndOneDay() {
		LocalTime eight = LocalTime.of(8, 0);

		assertThatThrownBy(() -> new ShiftType("Z", eight, Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new ShiftType("Z", eight, Duration.ofHours(-1))).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new ShiftType("Z", eight, Duration.ofHours(25))).isInstanceOf(IllegalArgumentException.class);
		assertThat(new ShiftType("Z", eight, Duration.ofHours(24)).length()).isEqualTo(Duration.ofHours(24));
	}

	@Test
	void shiftCodeMustNotBeBlank() {
		assertThatThrownBy(() -> new ShiftType(" ", LocalTime.NOON, Duration.ofHours(6)))
				.isInstanceOf(IllegalArgumentException.class);
	}

}
