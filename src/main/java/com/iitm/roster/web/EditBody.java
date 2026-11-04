package com.iitm.roster.web;

import com.iitm.roster.domain.Post;
import com.iitm.roster.domain.ShiftSlot;
import com.iitm.roster.domain.ShiftType;
import com.iitm.roster.domain.StandardShifts;
import com.iitm.roster.service.EditCommand;
import java.time.LocalDate;
import java.util.Set;

/**
 * JSON for {@code POST /api/rosters/{month}/edits}. {@code type} is {@code assign},
 * {@code unassign} or {@code move}. {@code shift} is {@code I}, {@code II} or {@code N}.
 */
public record EditBody(String type, LocalDate date, String shift, String staffId, String fromStaffId, String toStaffId,
		Post post) {

	EditCommand toCommand() {
		ShiftSlot slot = new ShiftSlot(date, shift(shift));
		return switch (type) {
			case "assign" -> new EditCommand.Assign(slot, staffId, post == null ? Post.GENERAL : post);
			case "unassign" -> new EditCommand.Unassign(slot, staffId);
			case "move" -> new EditCommand.Move(slot, fromStaffId, toStaffId);
			default -> throw new IllegalArgumentException("unknown edit type: " + type);
		};
	}

	/** Everyone whose shifts this edit changes. The policy needs the set. */
	Set<String> staffIds() {
		return switch (type) {
			case "assign", "unassign" -> Set.of(staffId);
			case "move" -> Set.of(fromStaffId, toStaffId);
			default -> throw new IllegalArgumentException("unknown edit type: " + type);
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

}
