package com.iitm.roster.web;

import com.iitm.roster.domain.Assignment;
import com.iitm.roster.domain.DayOff;
import com.iitm.roster.domain.Post;
import com.iitm.roster.domain.Roster;
import com.iitm.roster.domain.Staff;
import com.iitm.roster.domain.StandardShifts;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** One row of the roster sheet: the three shifts, who is post-duty off, and who has a weekly off. */
final class RosterGrid {

	private RosterGrid() {
	}

	static List<DayRow> rows(Roster roster) {
		List<DayRow> rows = new ArrayList<>();
		for (LocalDate day : roster.days()) {
			rows.add(new DayRow(day,
					people(roster, day, "I"),
					people(roster, day, "II"),
					people(roster, day, "N"),
					postDutyOff(roster, day),
					weeklyOff(roster, day)));
		}
		return List.copyOf(rows);
	}

	private static String people(Roster roster, LocalDate day, String shift) {
		return roster.assignments().stream()
				.filter(assignment -> assignment.slot().date().equals(day)
						&& assignment.slot().shift().code().equals(shift))
				.map(RosterGrid::labelled)
				.collect(Collectors.joining(", "));
	}

	private static String labelled(Assignment assignment) {
		return assignment.staff().name() + " " + tag(assignment.post());
	}

	private static String tag(Post post) {
		return switch (post) {
			case WARD -> "W";
			case CASUALTY -> "C";
			case GENERAL -> "G";
		};
	}

	/** The day after a night. A night on the last day of the previous month is not in this roster. */
	private static String postDutyOff(Roster roster, LocalDate day) {
		LocalDate night = day.minusDays(1);
		return roster.assignments().stream()
				.filter(assignment -> assignment.slot().date().equals(night)
						&& assignment.slot().shift().equals(StandardShifts.NIGHT))
				.map(assignment -> assignment.staff().name())
				.collect(Collectors.joining(", "));
	}

	private static String weeklyOff(Roster roster, LocalDate day) {
		return roster.weeklyOffs().stream()
				.filter(off -> off.date().equals(day))
				.map(DayOff::staff)
				.map(Staff::name)
				.collect(Collectors.joining(", "));
	}

	record DayRow(LocalDate date, String shiftI, String shiftII, String night, String postDutyOff, String weeklyOff) {
	}

}
