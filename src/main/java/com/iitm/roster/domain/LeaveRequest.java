package com.iitm.roster.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/** A request for leave. Approving it adds a {@link Leave}, which then blocks rostering. */
public record LeaveRequest(String id, Staff staff, LocalDate firstDay, LocalDate lastDay, RequestState state,
		Instant createdAt) {

	public LeaveRequest {
		if (id == null || id.isBlank()) {
			throw new IllegalArgumentException("id must not be blank");
		}
		Objects.requireNonNull(staff, "staff");
		Objects.requireNonNull(firstDay, "firstDay");
		Objects.requireNonNull(lastDay, "lastDay");
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(createdAt, "createdAt");
		if (lastDay.isBefore(firstDay)) {
			throw new IllegalArgumentException("leave ends before it starts: " + firstDay + " to " + lastDay);
		}
	}

	public LeaveRequest withState(RequestState newState) {
		return new LeaveRequest(id, staff, firstDay, lastDay, newState, createdAt);
	}

	public Leave toLeave() {
		return new Leave(staff, firstDay, lastDay);
	}

}
