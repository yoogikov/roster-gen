package com.iitm.roster.domain;

import java.util.Objects;

/**
 * A doctor or nurse who can be rostered.
 *
 * @param id stable identifier, unique across the hospital
 * @param name display name
 * @param role whether they are rostered as a doctor or a nurse
 */
public record Staff(String id, String name, StaffRole role) {

	public Staff {
		requireNonBlank(id, "id");
		requireNonBlank(name, "name");
		Objects.requireNonNull(role, "role");
	}

	private static void requireNonBlank(String value, String field) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(field + " must not be blank");
		}
	}

}
