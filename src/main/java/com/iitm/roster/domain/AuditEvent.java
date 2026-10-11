package com.iitm.roster.domain;

import java.time.Instant;
import java.time.YearMonth;
import java.util.Objects;

/**
 * One entry in the change log: who changed which month's roster, when, and what they did.
 *
 * @param summary human-readable description, e.g. "moved N on 2026-07-14 from S01 to S02"
 */
public record AuditEvent(Instant at, String actor, YearMonth month, String summary) {

	public AuditEvent {
		Objects.requireNonNull(at, "at");
		Objects.requireNonNull(month, "month");
		if (actor == null || actor.isBlank()) {
			throw new IllegalArgumentException("actor must not be blank");
		}
		if (summary == null || summary.isBlank()) {
			throw new IllegalArgumentException("summary must not be blank");
		}
	}

}
