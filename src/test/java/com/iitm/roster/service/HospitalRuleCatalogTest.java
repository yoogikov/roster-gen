package com.iitm.roster.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.iitm.roster.rules.InvalidRuleException;
import com.iitm.roster.rules.RuleSpec;
import com.iitm.roster.rules.RuleTemplate;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HospitalRuleCatalogTest {

	private final HospitalRuleCatalog catalog = new HospitalRuleCatalog();

	@Test
	void weekdayStaffingNeedsTwoNumbersAtLeastTwo() throws Exception {
		assertThat(catalog.templates()).extracting(RuleTemplate::id).contains("weekday-staffing", "post-duty-off");
		assertThat(catalog.build(new RuleSpec("weekday-staffing", Map.of("weekday", "4", "weekend", "2"))).id())
				.isEqualTo("min-staffing");
		assertThatThrownBy(() -> catalog.build(new RuleSpec("weekday-staffing", Map.of("weekday", "1", "weekend", "2"))))
				.isInstanceOf(InvalidRuleException.class);
	}

	@Test
	void aFixedRuleRejectsParameters() {
		assertThatThrownBy(() -> catalog.build(new RuleSpec("post-duty-off", Map.of("days", "1"))))
				.isInstanceOf(InvalidRuleException.class);
	}

}
