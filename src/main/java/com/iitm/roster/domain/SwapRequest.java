package com.iitm.roster.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * A request to exchange two assignments: {@code offered} belongs to the proposer and
 * {@code wanted} to the other staff member. The state machine lives in {@code requests};
 * this record only holds the data.
 */
public record SwapRequest(String id, Assignment offered, Assignment wanted, RequestState state, Instant createdAt) {

	public SwapRequest {
		if (id == null || id.isBlank()) {
			throw new IllegalArgumentException("id must not be blank");
		}
		Objects.requireNonNull(offered, "offered");
		Objects.requireNonNull(wanted, "wanted");
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(createdAt, "createdAt");
		if (offered.staff().equals(wanted.staff())) {
			throw new IllegalArgumentException("cannot swap with yourself");
		}
	}

	public SwapRequest withState(RequestState newState) {
		return new SwapRequest(id, offered, wanted, newState, createdAt);
	}

}
