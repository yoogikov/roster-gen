package com.iitm.roster.repo;

import com.iitm.roster.domain.AuditEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.YearMonth;

/** An audit row. Rows are only ever inserted, never updated or deleted. */
@Entity
@Table(name = "audit_event")
class AuditEventEntity {

	@Id
	@GeneratedValue
	private Long id;

	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

	@Column(nullable = false)
	private String actor;

	@Column(name = "roster_month", nullable = false, length = 7)
	private String month;

	@Column(nullable = false, length = 1000)
	private String summary;

	protected AuditEventEntity() {
		// Required by JPA, which instantiates the class before setting fields; not for application code.
	}

	AuditEventEntity(AuditEvent event) {
		this.occurredAt = event.at();
		this.actor = event.actor();
		this.month = event.month().toString();
		this.summary = event.summary();
	}

	AuditEvent toDomain() {
		return new AuditEvent(occurredAt, actor, YearMonth.parse(month), summary);
	}

}
