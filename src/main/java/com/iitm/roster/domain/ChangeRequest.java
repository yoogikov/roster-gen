package com.iitm.roster.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;
import java.util.Set;

/**
 * An edit that was not applied immediately because it needs someone else's approval.
 * {@code assign} and {@code unassign} name {@code staffId}; {@code move} names both ends.
 */
public record ChangeRequest(String id, YearMonth month, String type, LocalDate date, String shiftCode, String staffId,
		String fromStaffId, String toStaffId, Post post, RequestState state, Instant createdAt) {

	private static final Set<String> TYPES = Set.of("assign", "unassign", "move");
	private static final Set<String> SHIFTS = Set.of("I", "II", "N");

	public ChangeRequest {
		if (id == null || id.isBlank()) {
			throw new IllegalArgumentException("id must not be blank");
		}
		Objects.requireNonNull(month, "month");
		Objects.requireNonNull(date, "date");
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(createdAt, "createdAt");
		if (!TYPES.contains(type)) {
			throw new IllegalArgumentException("unknown edit type: " + type);
		}
		if (!SHIFTS.contains(shiftCode)) {
			throw new IllegalArgumentException("unknown shift: " + shiftCode);
		}
		if (!YearMonth.from(date).equals(month)) {
			throw new IllegalArgumentException("edit date is not in " + month);
		}
		switch (type) {
			case "assign" -> {
				requireId(staffId, "staffId");
				Objects.requireNonNull(post, "post");
			}
			case "unassign" -> requireId(staffId, "staffId");
			case "move" -> {
				requireId(fromStaffId, "fromStaffId");
				requireId(toStaffId, "toStaffId");
				if (fromStaffId.equals(toStaffId)) {
					throw new IllegalArgumentException("cannot move a slot to the same person: " + fromStaffId);
				}
			}
			default -> throw new IllegalArgumentException("unknown edit type: " + type);
		}
	}

	public ChangeRequest withState(RequestState newState) {
		return new ChangeRequest(id, month, type, date, shiftCode, staffId, fromStaffId, toStaffId, post, newState,
				createdAt);
	}

	private static void requireId(String id, String field) {
		if (id == null || id.isBlank()) {
			throw new IllegalArgumentException(field + " must not be blank");
		}
	}

}
