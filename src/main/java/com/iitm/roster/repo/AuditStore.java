package com.iitm.roster.repo;

import com.iitm.roster.domain.AuditEvent;
import java.time.YearMonth;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Append-only audit log. There is deliberately no update or delete. */
@Component
@Transactional
public class AuditStore {

	private final AuditEventJpaRepository events;

	AuditStore(AuditEventJpaRepository events) {
		this.events = events;
	}

	public void append(AuditEvent event) {
		events.save(new AuditEventEntity(event));
	}

	/** Every event, oldest first; events at the same instant stay in insertion order. */
	@Transactional(readOnly = true)
	public List<AuditEvent> findAll() {
		return events.findAllByOrderByOccurredAtAscIdAsc().stream().map(AuditEventEntity::toDomain).toList();
	}

	/** Events for one month's roster, oldest first. */
	@Transactional(readOnly = true)
	public List<AuditEvent> findByMonth(YearMonth month) {
		return events.findByMonthOrderByOccurredAtAscIdAsc(month.toString()).stream()
				.map(AuditEventEntity::toDomain)
				.toList();
	}

}
