package com.iitm.roster.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.iitm.roster.domain.Actor;
import com.iitm.roster.domain.Assignment;
import com.iitm.roster.domain.Role;
import com.iitm.roster.domain.Roster;
import com.iitm.roster.domain.ShiftSlot;
import com.iitm.roster.domain.ShiftType;
import com.iitm.roster.domain.Staff;
import com.iitm.roster.domain.StaffRole;
import com.iitm.roster.repo.AuditStore;
import com.iitm.roster.repo.RosterStore;
import com.iitm.roster.repo.StaffStore;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Uses the Spring-managed {@link RosterService}, with the test's own transaction turned
 * off, so the service's {@code @Transactional} really commits or rolls back. Each test uses
 * its own month because committed rows stay in this test context's database.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({RosterService.class, ServiceConfig.class, RosterStore.class, StaffStore.class, AuditStore.class})
class RosterServiceTransactionTest {

	private static final ShiftType MORNING = new ShiftType("M", LocalTime.of(8, 0), Duration.ofHours(6));
	private static final Staff DOC_A = new Staff("S01", "Test Doctor A", StaffRole.DOCTOR);

	@Autowired
	private RosterService service;

	@Autowired
	private RosterStore rosters;

	@Autowired
	private StaffStore staff;

	@Autowired
	private AuditStore audit;

	@BeforeEach
	void saveStaff() {
		staff.save(DOC_A);
	}

	@Test
	void validEditCommitsRosterAndAuditTogether() {
		YearMonth month = YearMonth.of(2026, 8);
		rosters.save(Roster.empty(month));
		ShiftSlot slot = new ShiftSlot(month.atDay(3), MORNING);

		service.applyEdit(new EditCommand.Assign(slot, "S01"), new Actor("admin", Role.ADMIN, Optional.empty()));

		assertThat(rosters.findByMonth(month).orElseThrow().assignments()).containsExactly(new Assignment(slot, DOC_A));
		assertThat(audit.findByMonth(month)).hasSize(1);
	}

	@Test
	void failureWritingTheAuditEventRollsBackTheRosterChange() {
		YearMonth month = YearMonth.of(2026, 9);
		rosters.save(Roster.empty(month));
		LocalDate day = month.atDay(3);
		// Longer than the 255-character actor column, so the audit insert fails after the roster was saved.
		Actor tooLong = new Actor("a".repeat(300), Role.ADMIN, Optional.empty());

		assertThatThrownBy(() -> service.applyEdit(new EditCommand.Assign(new ShiftSlot(day, MORNING), "S01"), tooLong))
				.isInstanceOf(DataIntegrityViolationException.class);

		assertThat(rosters.findByMonth(month)).contains(Roster.empty(month));
		assertThat(audit.findByMonth(month)).isEmpty();
	}

}
