package com.iitm.roster.web;

import com.iitm.roster.domain.Assignment;
import com.iitm.roster.domain.Post;
import com.iitm.roster.domain.Roster;
import com.iitm.roster.domain.ShiftSlot;
import com.iitm.roster.domain.ShiftType;
import com.iitm.roster.domain.StandardShifts;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Dropdown values for the sheet's swap and change forms: date|shift|staffId|post. */
final class SheetShifts {

	private SheetShifts() {
	}

	static List<Choice> mine(Roster roster, String staffId) {
		return choices(roster.assignments().stream().filter(a -> a.staff().id().equals(staffId)).toList());
	}

	static List<Choice> others(Roster roster, String staffId) {
		return choices(roster.assignments().stream().filter(a -> !a.staff().id().equals(staffId)).toList());
	}

	static List<Choice> all(Roster roster) {
		return choices(roster.assignments());
	}

	static Assignment parse(Roster roster, String value) {
		String[] parts = value.split("\\|", 4);
		if (parts.length != 4) {
			throw new IllegalArgumentException("pick a shift from the list");
		}
		LocalDate date = LocalDate.parse(parts[0]);
		ShiftType shift = shift(parts[1]);
		String staffId = parts[2];
		Post post = Post.valueOf(parts[3]);
		ShiftSlot slot = new ShiftSlot(date, shift);
		return roster.assignmentsOn(slot).stream()
				.filter(a -> a.staff().id().equals(staffId) && a.post() == post)
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						staffId + " is not on " + parts[1] + " (" + post + ") on " + date));
	}

	private static List<Choice> choices(List<Assignment> assignments) {
		List<Choice> choices = new ArrayList<>();
		assignments.stream()
				.sorted(Comparator.comparing((Assignment a) -> a.slot().date())
						.thenComparing(a -> a.slot().shift().code())
						.thenComparing(a -> a.staff().id()))
				.forEach(a -> choices.add(new Choice(encode(a), label(a))));
		return List.copyOf(choices);
	}

	private static String encode(Assignment assignment) {
		return assignment.slot().date() + "|" + assignment.slot().shift().code() + "|"
				+ assignment.staff().id() + "|" + assignment.post().name();
	}

	private static String label(Assignment assignment) {
		return assignment.slot().date() + " " + assignment.slot().shift().code() + " "
				+ tag(assignment.post()) + " — " + assignment.staff().name();
	}

	private static String tag(Post post) {
		return switch (post) {
			case WARD -> "W";
			case CASUALTY -> "C";
			case GENERAL -> "G";
		};
	}

	private static ShiftType shift(String code) {
		return switch (code) {
			case "I" -> StandardShifts.SHIFT_I;
			case "II" -> StandardShifts.SHIFT_II;
			case "N" -> StandardShifts.NIGHT;
			default -> throw new IllegalArgumentException("unknown shift: " + code);
		};
	}

	record Choice(String value, String label) {
	}

}
