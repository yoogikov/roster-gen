package com.iitm.roster.repo;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface AuditEventJpaRepository extends JpaRepository<AuditEventEntity, Long> {

	List<AuditEventEntity> findAllByOrderByOccurredAtAscIdAsc();

	List<AuditEventEntity> findByMonthOrderByOccurredAtAscIdAsc(String month);

}
