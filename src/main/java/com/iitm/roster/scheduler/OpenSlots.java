package com.iitm.roster.scheduler;

import com.iitm.roster.constraints.StaffingRequirement;
import com.iitm.roster.domain.Roster;
import com.iitm.roster.domain.ShiftSlot;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Works out which slots still need people. */
public final class OpenSlots {

	private OpenSlots() {
	}

	/**
	 * One {@link OpenSlot} for every person missing from {@code roster} under
	 * {@code requirements}, day by day through the month.
	 */
	public static List<OpenSlot> missing(Roster roster, List<StaffingRequirement> requirements) {
		List<OpenSlot> open = new ArrayList<>();
		for (LocalDate day : roster.days()) {
			for (StaffingRequirement requirement : requirements) {
				ShiftSlot slot = new ShiftSlot(day, requirement.shift());
				long present = roster.assignmentsOn(slot).stream()
						.filter(a -> a.staff().role() == requirement.role())
						.count();
				for (long i = present; i < requirement.minimum(); i++) {
					open.add(new OpenSlot(slot, requirement.role()));
				}
			}
		}
		return open;
	}

}
