package com.iitm.roster.repo;

import java.time.YearMonth;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** The rules saved from the editor, one row per save. */
@Component
@Transactional
public class SavedRuleStore {

	private final SavedRuleJpaRepository rules;

	SavedRuleStore(SavedRuleJpaRepository rules) {
		this.rules = rules;
	}

	public void save(SavedRule rule) {
		SavedRuleEntity entity = rules.findById(rule.id()).orElseGet(() -> new SavedRuleEntity(rule.id()));
		entity.update(rule);
		rules.save(entity);
	}

	@Transactional(readOnly = true)
	public List<SavedRule> findByMonth(YearMonth month) {
		return rules.findByMonthOrderByTemplateIdAsc(month.toString()).stream().map(SavedRuleEntity::toDomain).toList();
	}

}
