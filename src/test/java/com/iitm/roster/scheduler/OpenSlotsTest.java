package com.iitm.roster.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import com.iitm.roster.constraints.StaffingRequirement;
import com.iitm.roster.domain.Assignment;
import com.iitm.roster.domain.Roster;
import com.iitm.roster.domain.ShiftSlot;
import com.iitm.roster.domain.ShiftType;
import com.iitm.roster.domain.Staff;
import com.iitm.roster.domain.StaffRole;
import java.time.Duration;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

class OpenSlotsTest {

	private static final ShiftType MORNING = new ShiftType("M", LocalTime.of(8, 0), Duration.ofHours(6));
	private static final YearMonth JULY = YearMonth.of(2026, 7);
	private static final Staff DOC = new Staff("D01", "Test Doctor 1", StaffRole.DOCTOR);
	private static final Staff NURSE = new Staff("N01", "Test Nurse 1", StaffRole.NURSE);

	@Test
	void emptyRosterNeedsEveryPersonOnEveryDay() {
		List<OpenSlot> open = OpenSlots.missing(Roster.empty(JULY),
				List.of(new StaffingRequirement(MORNING, StaffRole.DOCTOR, 2)));

		assertThat(open).hasSize(62);
	}

	@Test
	void peopleAlreadyThereOfTheRightRoleAreSubtracted() {
		ShiftSlot first = new ShiftSlot(JULY.atDay(1), MORNING);
		Roster roster = Roster.empty(JULY).withAssignment(new Assignment(first, DOC)).withAssignment(new Assignment(first, NURSE));

		List<OpenSlot> open = OpenSlots.missing(roster, List.of(new StaffingRequirement(MORNING, StaffRole.DOCTOR, 2)));

		assertThat(open).hasSize(61);
		assertThat(open).filteredOn(o -> o.slot().equals(first)).containsExactly(new OpenSlot(first, StaffRole.DOCTOR));
	}

	@Test
	void zeroMinimumNeedsNobody() {
		assertThat(OpenSlots.missing(Roster.empty(JULY), List.of(new StaffingRequirement(MORNING, StaffRole.NURSE, 0)))).isEmpty();
	}

}
