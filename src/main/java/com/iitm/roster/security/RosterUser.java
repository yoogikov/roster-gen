package com.iitm.roster.security;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

/** A dev login, with the staff id it acts as when the account is a doctor. */
public final class RosterUser extends User {

	private final String role;
	private final String staffId;

	public RosterUser(String username, String password, String role, String staffId) {
		super(username, password, List.of(new SimpleGrantedAuthority("ROLE_" + Objects.requireNonNull(role, "role"))));
		this.role = role;
		this.staffId = staffId;
	}

	/** The role name without the {@code ROLE_} prefix, so a copy does not parse the authority. */
	public String role() {
		return role;
	}

	public Optional<String> staffId() {
		if (staffId == null || staffId.isBlank()) {
			return Optional.empty();
		}
		return Optional.of(staffId);
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof RosterUser user
				&& role.equals(user.role)
				&& staffId().equals(user.staffId())
				&& super.equals(other);
	}

	@Override
	public int hashCode() {
		return Objects.hash(super.hashCode(), role, staffId);
	}

}
