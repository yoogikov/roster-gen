package com.iitm.roster.export;

import static com.iitm.roster.domain.StandardShifts.NIGHT;
import static com.iitm.roster.domain.StandardShifts.SHIFT_I;
import static com.iitm.roster.domain.StandardShifts.SHIFT_II;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.iitm.roster.domain.Assignment;
import com.iitm.roster.domain.DayOff;
import com.iitm.roster.domain.Post;
import com.iitm.roster.domain.Roster;
import com.iitm.roster.domain.RosterStatus;
import com.iitm.roster.domain.ShiftSlot;
import com.iitm.roster.domain.ShiftType;
import com.iitm.roster.domain.Staff;
import com.iitm.roster.domain.StaffRole;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

class CsvRosterExporterTest {

	private static final YearMonth OCTOBER = YearMonth.of(2026, 10);
	private static final Staff D1 = doctor(1);
	private static final Staff D2 = doctor(2);
	private static final Staff D3 = doctor(3);
	private static final Staff D4 = doctor(4);
	private static final Staff D5 = doctor(5);

	private final CsvRosterExporter exporter = new CsvRosterExporter(SHIFT_I, SHIFT_II, NIGHT);

	private static Staff doctor(int n) {
		return new Staff(String.format("D%02d", n), "Test Doctor " + n, StaffRole.DOCTOR);
	}

	private static Assignment at(int day, ShiftType shift, Staff staff, Post post) {
		return new Assignment(new ShiftSlot(OCTOBER.atDay(day), shift), staff, post);
	}

	private List<String> lines(Roster roster) {
		return new String(exporter.export(roster, Format.CSV), StandardCharsets.UTF_8).lines().toList();
	}

	@Test
	void oneRowPerDayAfterTheHeader() {
		List<String> lines = lines(Roster.empty(OCTOBER));

		assertThat(lines).hasSize(32);
		assertThat(lines.get(0)).isEqualTo(CsvRosterExporter.HEADER);
		assertThat(lines.get(1)).isEqualTo("2026-10-01,Thu,,,,,,,,,");
		assertThat(lines.get(31)).startsWith("2026-10-31,Sat,");
	}

	@Test
	void eachDoctorLandsInTheColumnForTheirShiftAndPost() {
		Roster roster = new Roster(OCTOBER, RosterStatus.DRAFT, List.of(
				at(1, NIGHT, D1, Post.GENERAL), at(1, SHIFT_I, D1, Post.WARD), at(1, SHIFT_I, D2, Post.CASUALTY),
				at(1, SHIFT_I, D3, Post.GENERAL), at(1, SHIFT_I, D4, Post.GENERAL),
				at(1, SHIFT_II, D5, Post.WARD)),
				List.of());

		assertThat(lines(roster).get(1)).isEqualTo(
				"2026-10-01,Thu,Test Doctor 1,Test Doctor 1,Test Doctor 2,Test Doctor 3; Test Doctor 4,Test Doctor 5,,,,");
	}

	@Test
	void postDutyOffIsTheDayAfterANightAndWeeklyOffsAreListed() {
		Roster roster = new Roster(OCTOBER, RosterStatus.DRAFT, List.of(at(1, NIGHT, D1, Post.GENERAL)),
				List.of(new DayOff(D2, OCTOBER.atDay(2)), new DayOff(D3, OCTOBER.atDay(2)), new DayOff(D4, OCTOBER.atDay(3))));

		assertThat(lines(roster).get(2)).isEqualTo("2026-10-02,Fri,,,,,,,,Test Doctor 1,Test Doctor 2; Test Doctor 3");
	}

	@Test
	void theFirstDayHasNoPostDutyOffFromLastMonth() {
		Roster roster = new Roster(OCTOBER, RosterStatus.DRAFT, List.of(at(31, NIGHT, D1, Post.GENERAL)));

		assertThat(lines(roster).get(1)).endsWith(",,");
		assertThat(lines(roster)).hasSize(32);
	}

	@Test
	void namesWithCommasOrQuotesAreQuoted() {
		assertThat(CsvRosterExporter.field("Rao, A")).isEqualTo("\"Rao, A\"");
		assertThat(CsvRosterExporter.field("A \"Doc\" B")).isEqualTo("\"A \"\"Doc\"\" B\"");
		assertThat(CsvRosterExporter.field("line\nbreak")).isEqualTo("\"line\nbreak\"");
		assertThat(CsvRosterExporter.field("plain")).isEqualTo("plain");
	}

	@Test
	void onlyCsvIsSupported() {
		assertThatThrownBy(() -> exporter.export(Roster.empty(OCTOBER), Format.PDF))
				.isInstanceOf(IllegalArgumentException.class);
	}

}
