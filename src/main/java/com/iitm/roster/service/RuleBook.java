package com.iitm.roster.service;

import com.iitm.roster.repo.MonthInputStore;
import com.iitm.roster.repo.SavedRule;
import com.iitm.roster.repo.SavedRuleStore;
import com.iitm.roster.repo.StoredMonthInput;
import com.iitm.roster.rules.ConstraintFactory;
import com.iitm.roster.rules.InvalidRuleException;
import com.iitm.roster.rules.RuleSpec;
import com.iitm.roster.rules.RuleTemplate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saves a rule from the editor. Weekday staffing also updates the stored month input,
 * so the next edit is checked against the new numbers.
 */
@Service
public class RuleBook {

	private final ConstraintFactory factory;
	private final SavedRuleStore rules;
	private final MonthInputStore inputs;

	public RuleBook(ConstraintFactory factory, SavedRuleStore rules, MonthInputStore inputs) {
		this.factory = factory;
		this.rules = rules;
		this.inputs = inputs;
	}

	public List<RuleTemplate> templates() {
		return factory.templates();
	}

	@Transactional(readOnly = true)
	public List<SavedRule> saved(YearMonth month) {
		return rules.findByMonth(month);
	}

	@Transactional
	public SavedRule save(YearMonth month, RuleSpec spec) throws InvalidRuleException {
		Objects.requireNonNull(month, "month");
		factory.build(spec);
		if ("weekday-staffing".equals(spec.templateId())) {
			applyStaffing(month, spec.params());
		} else if (inputs.findByMonth(month).isEmpty()) {
			throw new InvalidRuleException("no roster input for " + month);
		}
		SavedRule saved = new SavedRule(UUID.randomUUID().toString(), month, spec.templateId(), spec.params());
		rules.save(saved);
		return saved;
	}

	private void applyStaffing(YearMonth month, Map<String, String> params) throws InvalidRuleException {
		StoredMonthInput stored = inputs.findByMonth(month)
				.orElseThrow(() -> new InvalidRuleException("no roster input for " + month));
		int weekday = Integer.parseInt(params.get("weekday").trim());
		int weekend = Integer.parseInt(params.get("weekend").trim());
		inputs.save(new StoredMonthInput(stored.month(), stored.doctors(), stored.firstOnNights(), stored.leaves(),
				stored.calendar(), weekday, weekend));
	}

}
