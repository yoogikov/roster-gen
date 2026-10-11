package com.iitm.roster.rules;

import java.util.Map;
import java.util.Objects;

/**
 * A rule as the CMO saved it: which template, and the values she filled in.
 *
 * @param params parameter name to value, as entered in the form
 */
public record RuleSpec(String templateId, Map<String, String> params) {

	public RuleSpec {
		Objects.requireNonNull(templateId, "templateId");
		params = Map.copyOf(params);
	}

}
