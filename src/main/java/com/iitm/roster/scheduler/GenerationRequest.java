package com.iitm.roster.scheduler;

import com.iitm.roster.constraints.Constraint;
import com.iitm.roster.domain.Roster;
import com.iitm.roster.domain.ShiftSlot;
import com.iitm.roster.domain.Staff;
import java.util.List;
import java.util.Objects;

/**
 * What to fill and with whom.
 *
 * @param start the roster to build on; its assignments are kept (an empty roster to start from scratch)
 * @param staff everyone who may be assigned
 * @param openSlots the slots that still need someone, one entry per person needed
 * @param constraints the rules the result must satisfy
 */
public record GenerationRequest(Roster start, List<Staff> staff, List<ShiftSlot> openSlots, List<Constraint> constraints) {

	public GenerationRequest {
		Objects.requireNonNull(start, "start");
		staff = List.copyOf(staff);
		openSlots = List.copyOf(openSlots);
		constraints = List.copyOf(constraints);
	}

}
