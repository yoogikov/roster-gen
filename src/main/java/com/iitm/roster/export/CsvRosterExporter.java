package com.iitm.roster.export;

import com.iitm.roster.domain.Assignment;
import com.iitm.roster.domain.DayOff;
import com.iitm.roster.domain.Post;
import com.iitm.roster.domain.Roster;
import com.iitm.roster.domain.ShiftSlot;
import com.iitm.roster.domain.ShiftType;
import com.iitm.roster.domain.Staff;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Writes a hospital roster as CSV, one row per day, laid out like the CMO's sheet: who is on
 * nights, who is ward, casualty and general duty on each day shift, and who is off.
 *
 * <p>Post-duty off is not stored in the roster; it is shown here as the day after a night.
 * Several doctors in one cell are separated by {@code "; "}. Doctors appear by name.
 */
public final class CsvRosterExporter implements RosterExporter {

	static final String HEADER = "date,day,night,shift I ward,shift I casualty,shift I general,"
			+ "shift II ward,shift II casualty,shift II general,post-duty off,weekly off";

	private final ShiftType morning;
	private final ShiftType afternoon;
	private final ShiftType night;

	public CsvRosterExporter(ShiftType morning, ShiftType afternoon, ShiftType night) {
		this.morning = Objects.requireNonNull(morning, "morning");
		this.afternoon = Objects.requireNonNull(afternoon, "afternoon");
		this.night = Objects.requireNonNull(night, "night");
	}

	@Override
	public byte[] export(Roster roster, Format format) {
		if (format != Format.CSV) {
			throw new IllegalArgumentException("this exporter writes CSV only, not " + format);
		}
		StringBuilder csv = new StringBuilder(HEADER).append('\n');
		for (LocalDate day : roster.days()) {
			List<String> row = new ArrayList<>();
			row.add(day.toString());
			row.add(day.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
			row.add(names(roster.assignmentsOn(new ShiftSlot(day, night))));
			for (ShiftType shift : List.of(morning, afternoon)) {
				List<Assignment> on = roster.assignmentsOn(new ShiftSlot(day, shift));
				for (Post post : List.of(Post.WARD, Post.CASUALTY, Post.GENERAL)) {
					row.add(names(on.stream().filter(a -> a.post() == post).toList()));
				}
			}
			row.add(postDutyOff(roster, day));
			row.add(roster.weeklyOffs().stream().filter(off -> off.date().equals(day))
					.map(DayOff::staff).map(Staff::name).collect(Collectors.joining("; ")));
			csv.append(row.stream().map(CsvRosterExporter::field).collect(Collectors.joining(","))).append('\n');
		}
		return csv.toString().getBytes(StandardCharsets.UTF_8);
	}

	/** Whoever did last night. On the 1st that is last month's roster, so nobody here. */
	private String postDutyOff(Roster roster, LocalDate day) {
		return names(roster.assignmentsOn(new ShiftSlot(day.minusDays(1), night)));
	}

	private static String names(List<Assignment> assignments) {
		return assignments.stream().map(a -> a.staff().name()).collect(Collectors.joining("; "));
	}

	/** Quotes a field only when it needs it (RFC 4180). */
	static String field(String value) {
		if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
			return '"' + value.replace("\"", "\"\"") + '"';
		}
		return value;
	}

}
