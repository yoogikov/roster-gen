package com.iitm.roster.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RecordValidationTest {

	private static final Staff DOC_A = new Staff("S01", "Test Doctor A", StaffRole.DOCTOR);
	private static final Staff DOC_B = new Staff("S02", "Test Doctor B", StaffRole.DOCTOR);
	private static final ShiftType MORNING = new ShiftType("M", LocalTime.of(8, 0), Duration.ofHours(6));

	@Test
	void leaveCoversBothEndDaysAndNothingOutside() {
		Leave leave = new Leave(DOC_A, LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 12));

		assertThat(leave.covers(LocalDate.of(2026, 7, 10))).isTrue();
		assertThat(leave.covers(LocalDate.of(2026, 7, 12))).isTrue();
		assertThat(leave.covers(LocalDate.of(2026, 7, 9))).isFalse();
		assertThat(leave.covers(LocalDate.of(2026, 7, 13))).isFalse();
	}

	@Test
	void leaveCannotEndBeforeItStarts() {
		assertThatThrownBy(() -> new Leave(DOC_A, LocalDate.of(2026, 7, 12), LocalDate.of(2026, 7, 10)))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void staffNeedsAnIdAndName() {
		assertThatThrownBy(() -> new Staff("", "Test Doctor A", StaffRole.DOCTOR)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new Staff("S01", null, StaffRole.DOCTOR)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void swapRequestCannotBeWithYourself() {
		Assignment first = new Assignment(new ShiftSlot(LocalDate.of(2026, 7, 1), MORNING), DOC_A);
		Assignment second = new Assignment(new ShiftSlot(LocalDate.of(2026, 7, 2), MORNING), DOC_A);

		assertThatThrownBy(() -> new SwapRequest("R1", first, second, RequestState.PENDING, Instant.EPOCH))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void swapRequestWithStateChangesOnlyTheState() {
		Assignment offered = new Assignment(new ShiftSlot(LocalDate.of(2026, 7, 1), MORNING), DOC_A);
		Assignment wanted = new Assignment(new ShiftSlot(LocalDate.of(2026, 7, 2), MORNING), DOC_B);
		SwapRequest pending = new SwapRequest("R1", offered, wanted, RequestState.PENDING, Instant.EPOCH);

		SwapRequest accepted = pending.withState(RequestState.ACCEPTED);

		assertThat(accepted).isEqualTo(new SwapRequest("R1", offered, wanted, RequestState.ACCEPTED, Instant.EPOCH));
	}

	@Test
	void onlyPendingAndAcceptedAreOpen() {
		assertThat(RequestState.PENDING.isOpen()).isTrue();
		assertThat(RequestState.ACCEPTED.isOpen()).isTrue();
		assertThat(RequestState.APPROVED.isOpen()).isFalse();
		assertThat(RequestState.REJECTED.isOpen()).isFalse();
		assertThat(RequestState.LAPSED.isOpen()).isFalse();
	}

	@Test
	void actorNeedsAUsernameAndMayHaveNoStaffRecord() {
		assertThat(new Actor("reception", Role.RECEPTION, Optional.empty()).staffId()).isEmpty();
		assertThatThrownBy(() -> new Actor(" ", Role.ADMIN, Optional.empty())).isInstanceOf(IllegalArgumentException.class);
	}

}
