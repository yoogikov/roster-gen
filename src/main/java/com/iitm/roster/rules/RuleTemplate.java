package com.iitm.roster.rules;

import java.util.List;
import java.util.Objects;

/**
 * A rule shape the CMO can pick, such as "at most N consecutive nights".
 *
 * @param parameterNames the values she must fill in, e.g. {@code ["maxNights"]}
 */
public record RuleTemplate(String id, String description, List<String> parameterNames) {

	public RuleTemplate {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(description, "description");
		parameterNames = List.copyOf(parameterNames);
	}

}
