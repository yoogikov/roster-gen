package com.iitm.roster.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import com.iitm.roster.domain.DayOff;
import com.iitm.roster.domain.Leave;
import com.iitm.roster.domain.Roster;
import com.iitm.roster.domain.RosterStatus;
import com.iitm.roster.domain.Staff;
import com.iitm.roster.domain.StaffRole;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class OwedOffsTest {

	/** October 2026 counts five weeks: Thu 1 - Sun 4, then Mondays 5, 12, 19 and 26. */
	private static final YearMonth OCTOBER = YearMonth.of(2026, 10);
	private static final Staff D1 = new Staff("D01", "Test Doctor 1", StaffRole.DOCTOR);
	private static final Staff D2 = new Staff("D02", "Test Doctor 2", StaffRole.DOCTOR);

	private static Roster roster(int... days) {
		List<DayOff> offs = new ArrayList<>();
		for (int day : days) {
			offs.add(new DayOff(D1, OCTOBER.atDay(day)));
		}
		return new Roster(OCTOBER, RosterStatus.DRAFT, List.of(), offs);
	}

	@Test
	void anOffInEveryWeekOwesNothing() {
		assertThat(OwedOffs.after(roster(4, 11, 18, 25, 31), List.of(D1), List.of(), List.of())).isEmpty();
	}

	@Test
	void eachWeekWithoutAnOffIsOwedOnce() {
		assertThat(OwedOffs.after(roster(4, 25, 31), List.of(D1), List.of(), List.of())).containsExactly(D1, D1);
	}

	@Test
	void aWeekAllOnLeaveOwesNothingButPartOfAWeekStillDoes() {
		Leave wholeWeek = new Leave(D1, OCTOBER.atDay(12), OCTOBER.atDay(18));
		assertThat(OwedOffs.after(roster(4, 11, 25, 31), List.of(D1), List.of(wholeWeek), List.of())).isEmpty();

		Leave partOfIt = new Leave(D1, OCTOBER.atDay(12), OCTOBER.atDay(17));
		assertThat(OwedOffs.after(roster(4, 11, 25, 31), List.of(D1), List.of(partOfIt), List.of())).containsExactly(D1);
	}

	@Test
	void anExtraOffPaysOffWhatWasOwed() {
		// Two offs in the week of the 5th: one is the week's own, the other pays a debt.
		assertThat(OwedOffs.after(roster(4, 5, 11, 18, 25, 31), List.of(D1), List.of(), List.of(D1))).isEmpty();
	}

	@Test
	void whatTheMonthCouldNotGiveStaysOwedAlongsideNewlyMissedOffs() {
		assertThat(OwedOffs.after(roster(4, 11, 18, 25, 31), List.of(D1), List.of(), List.of(D1))).containsExactly(D1);
		assertThat(OwedOffs.after(roster(4, 11, 18, 31), List.of(D1), List.of(), List.of(D1))).containsExactly(D1, D1);
	}

	@Test
	void anExtraOffPaysOnlyOneDebtAndSurplusIsNotBanked() {
		assertThat(OwedOffs.after(roster(4, 5, 11, 18, 25, 31), List.of(D1), List.of(), List.of(D1, D1))).containsExactly(D1);
		// An extra off with no debt does not cover a later missed week.
		assertThat(OwedOffs.after(roster(4, 5, 11, 18, 31), List.of(D1), List.of(), List.of())).containsExactly(D1);
	}

	@Test
	void anOffInAShortWeekThatDoesNotCountIsAnExtra() {
		YearMonth january = YearMonth.of(2027, 1);
		// Fri 1 - Sun 3 January does not count. Mondays 4, 11, 18 and 25 do.
		List<DayOff> offs = List.of(new DayOff(D1, january.atDay(2)), new DayOff(D1, january.atDay(10)),
				new DayOff(D1, january.atDay(17)), new DayOff(D1, january.atDay(24)), new DayOff(D1, january.atDay(31)));
		Roster roster = new Roster(january, RosterStatus.DRAFT, List.of(), offs);

		assertThat(OwedOffs.after(roster, List.of(D1), List.of(), List.of(D1))).isEmpty();
		List<DayOff> withoutTheExtra = offs.subList(1, offs.size());
		assertThat(OwedOffs.after(new Roster(january, RosterStatus.DRAFT, List.of(), withoutTheExtra), List.of(D1), List.of(), List.of(D1)))
				.containsExactly(D1);
	}

	@Test
	void aDoctorNoLongerOnTheListIsDroppedAndTheNewNameIsKept() {
		Staff renamed = new Staff("D01", "Renamed", StaffRole.DOCTOR);
		Roster everyWeek = roster(4, 11, 18, 25);

		assertThat(OwedOffs.after(everyWeek, List.of(D2), List.of(), List.of())).containsExactly(D2, D2, D2, D2, D2);
		assertThat(OwedOffs.after(everyWeek, List.of(renamed), List.of(), List.of())).containsExactly(renamed);
	}

}
