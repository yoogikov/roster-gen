package com.iitm.roster.repo;

import static org.assertj.core.api.Assertions.assertThat;

import com.iitm.roster.domain.AuditEvent;
import java.time.Instant;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(AuditStore.class)
class AuditStoreTest {

	private static final YearMonth JULY = YearMonth.of(2026, 7);
	private static final YearMonth AUGUST = YearMonth.of(2026, 8);
	private static final Instant T0 = Instant.parse("2026-07-01T04:30:00Z");

	@Autowired
	private AuditStore audit;

	@Autowired
	private TestEntityManager em;

	@Test
	void eventsComeBackOldestFirst() {
		AuditEvent later = new AuditEvent(T0.plusSeconds(60), "admin", JULY, "second");
		AuditEvent earlier = new AuditEvent(T0, "admin", JULY, "first");

		audit.append(later);
		audit.append(earlier);
		flushAndClear();

		assertThat(audit.findAll()).containsExactly(earlier, later);
	}

	@Test
	void eventsAtTheSameInstantKeepInsertionOrder() {
		AuditEvent first = new AuditEvent(T0, "admin", JULY, "moved one");
		AuditEvent second = new AuditEvent(T0, "admin", JULY, "moved other");

		audit.append(first);
		audit.append(second);
		flushAndClear();

		assertThat(audit.findAll()).containsExactly(first, second);
	}

	@Test
	void findByMonthOnlyReturnsThatMonth() {
		AuditEvent july = new AuditEvent(T0, "admin", JULY, "july edit");
		AuditEvent august = new AuditEvent(T0, "admin", AUGUST, "august edit");

		audit.append(july);
		audit.append(august);
		flushAndClear();

		assertThat(audit.findByMonth(JULY)).containsExactly(july);
		assertThat(audit.findByMonth(AUGUST)).containsExactly(august);
	}

	@Test
	void emptyLogIsEmpty() {
		assertThat(audit.findAll()).isEmpty();
	}

	private void flushAndClear() {
		em.flush();
		em.clear();
	}

}
