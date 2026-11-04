package com.iitm.roster.repo;

import java.time.YearMonth;
import java.util.Map;

/** One saved rule: a template and the values the form filled in, for one month. */
public record SavedRule(String id, YearMonth month, String templateId, Map<String, String> params) {

	public SavedRule {
		if (id == null || id.isBlank()) {
			throw new IllegalArgumentException("id must not be blank");
		}
		if (templateId == null || templateId.isBlank()) {
			throw new IllegalArgumentException("templateId must not be blank");
		}
		params = Map.copyOf(params);
	}

}
