package com.iitm.roster.constraints;

import com.iitm.roster.domain.Assignment;
import com.iitm.roster.domain.Roster;
import com.iitm.roster.domain.ShiftSlot;
import com.iitm.roster.domain.Staff;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A roster's assignments grouped once, for a constraint that looks them up many times in one check.
 * {@link Roster#assignmentsOn} and {@link Roster#assignmentsFor} scan the whole month on every
 * call, and the generator runs every check thousands of times. Each list keeps the roster's order,
 * so a check gives the same violations in the same order as before.
 */
final class RosterIndex {

	private final Map<ShiftSlot, List<Assignment>> bySlot = new HashMap<>();
	private final Map<Staff, List<Assignment>> byStaff = new HashMap<>();

	RosterIndex(Roster roster) {
		for (Assignment assignment : roster.assignments()) {
			bySlot.computeIfAbsent(assignment.slot(), slot -> new ArrayList<>()).add(assignment);
			byStaff.computeIfAbsent(assignment.staff(), staff -> new ArrayList<>()).add(assignment);
		}
	}

	/** Same as {@link Roster#assignmentsOn}. */
	List<Assignment> on(ShiftSlot slot) {
		return bySlot.getOrDefault(slot, List.of());
	}

	/** Same as {@link Roster#assignmentsFor}. */
	List<Assignment> of(Staff staff) {
		return byStaff.getOrDefault(staff, List.of());
	}

}
