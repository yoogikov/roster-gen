package com.iitm.roster.repo;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface SavedRuleJpaRepository extends JpaRepository<SavedRuleEntity, String> {

	List<SavedRuleEntity> findByMonthOrderByTemplateIdAsc(String month);

}
