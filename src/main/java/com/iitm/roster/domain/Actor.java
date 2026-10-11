package com.iitm.roster.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * The signed-in user doing something. {@code staffId} links the login to a {@link Staff}
 * member; it is empty for accounts that are not rostered (e.g. reception).
 */
public record Actor(String username, Role role, Optional<String> staffId) {

	public Actor {
		if (username == null || username.isBlank()) {
			throw new IllegalArgumentException("username must not be blank");
		}
		Objects.requireNonNull(role, "role");
		Objects.requireNonNull(staffId, "staffId");
	}

}
