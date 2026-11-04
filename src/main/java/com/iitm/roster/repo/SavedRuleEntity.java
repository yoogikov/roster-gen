package com.iitm.roster.repo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "saved_rule")
class SavedRuleEntity {

	@Id
	private String id;

	@Column(name = "roster_month", nullable = false, length = 7)
	private String month;

	@Column(name = "template_id", nullable = false)
	private String templateId;

	/** {@code name=value} lines. Empty when the template has no parameters. */
	@Column(nullable = false, length = 2000)
	private String params;

	protected SavedRuleEntity() {
		// Required by JPA, which instantiates the class before setting fields; not for application code.
	}

	SavedRuleEntity(String id) {
		this.id = id;
	}

	void update(SavedRule rule) {
		this.month = rule.month().toString();
		this.templateId = rule.templateId();
		this.params = encode(rule.params());
	}

	SavedRule toDomain() {
		return new SavedRule(id, YearMonth.parse(month), templateId, decode(params));
	}

	private static String encode(Map<String, String> values) {
		StringBuilder encoded = new StringBuilder();
		for (Map.Entry<String, String> entry : values.entrySet()) {
			if (!encoded.isEmpty()) {
				encoded.append('\n');
			}
			encoded.append(entry.getKey()).append('=').append(entry.getValue());
		}
		return encoded.toString();
	}

	private static Map<String, String> decode(String encoded) {
		Map<String, String> values = new LinkedHashMap<>();
		if (encoded == null || encoded.isBlank()) {
			return values;
		}
		for (String line : encoded.split("\n")) {
			int split = line.indexOf('=');
			if (split > 0) {
				values.put(line.substring(0, split), line.substring(split + 1));
			}
		}
		return values;
	}

}
