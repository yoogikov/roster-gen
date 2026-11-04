package com.iitm.roster.requests;

import static org.assertj.core.api.Assertions.assertThat;

import com.iitm.roster.constraints.Constraint;
import com.iitm.roster.constraints.NoLeaveConflictConstraint;
import com.iitm.roster.constraints.Severity;
import com.iitm.roster.constraints.Violation;
import com.iitm.roster.domain.Actor;
import com.iitm.roster.domain.HospitalCalendar;
import com.iitm.roster.domain.Post;
import com.iitm.roster.domain.Role;
import com.iitm.roster.domain.Roster;
import com.iitm.roster.domain.RosterStatus;
import com.iitm.roster.domain.ShiftSlot;
import com.iitm.roster.domain.Staff;
import com.iitm.roster.domain.StaffRole;
import com.iitm.roster.domain.StandardShifts;
import com.iitm.roster.repo.AuditStore;
import com.iitm.roster.repo.ChangeRequestStore;
import com.iitm.roster.repo.LeaveRequestStore;
import com.iitm.roster.repo.MonthInputStore;
import com.iitm.roster.repo.RosterStore;
import com.iitm.roster.repo.StaffStore;
import com.iitm.roster.repo.StoredMonthInput;
import com.iitm.roster.security.HospitalAuthorizationPolicy;
import com.iitm.roster.service.EditCommand;
import com.iitm.roster.service.EditResult;
import com.iitm.roster.service.LeaveLedger;
import com.iitm.roster.service.RosterService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

/** Leave blocks a later assignment, and an edit that needs approval is applied only when an admin says so. */
@DataJpaTest
@Import({RosterStore.class, StaffStore.class, AuditStore.class, MonthInputStore.class, LeaveRequestStore.class,
		ChangeRequestStore.class, LeaveLedger.class})
class LeaveAndChangeTest {

	private static final YearMonth NOVEMBER = YearMonth.of(2026, 11);
	private static final Instant NOW = Instant.parse("2026-11-04T05:30:00Z");
	private static final Staff D01 = new Staff("D01", "Test Doctor 1", StaffRole.DOCTOR);
	private static final Staff D02 = new Staff("D02", "Test Doctor 2", StaffRole.DOCTOR);
	private static final Actor ADMIN = new Actor("admin", Role.ADMIN, Optional.empty());
	private static final Actor RECEPTION = new Actor("reception", Role.RECEPTION, Optional.empty());
	private static final Actor DOCTOR_1 = new Actor("staff1", Role.STAFF, Optional.of("D01"));

	@Autowired
	private RosterStore rosters;

	@Autowired
	private StaffStore staff;

	@Autowired
	private AuditStore audit;

	@Autowired
	private MonthInputStore inputs;

	@Autowired
	private LeaveRequestStore leaveRequests;

	@Autowired
	private ChangeRequestStore changeRequests;

	@Autowired
	private LeaveLedger ledger;

	private LeaveLifecycle leaves;
	private ChangeLifecycle changes;
	private RosterService edits;

	@BeforeEach
	void setUp() {
		staff.save(D01);
		staff.save(D02);
		inputs.save(new StoredMonthInput(NOVEMBER, List.of(D01, D02), D01, List.of(), HospitalCalendar.noHolidays(), 4, 2));
		rosters.save(Roster.empty(NOVEMBER).withStatus(RosterStatus.PUBLISHED));
		Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
		HospitalAuthorizationPolicy policy = new HospitalAuthorizationPolicy(clock);
		edits = new RosterService(rosters, staff, audit, month -> inputs.findByMonth(month)
				.<List<Constraint>>map(stored -> List.of(new NoLeaveConflictConstraint(stored.leaves(), Severity.HARD)))
				.orElse(List.of()), clock);
		leaves = new LeaveLifecycle(leaveRequests, staff, rosters, ledger, policy, clock);
		changes = new ChangeLifecycle(edits, rosters, changeRequests, policy, clock);
	}

	@Test
	void approvedLeaveBlocksRosteringThatDoctor() {
		LocalDate first = NOVEMBER.atDay(10);
		LeaveResult pending = leaves.propose(D01.id(), first, NOVEMBER.atDay(12), DOCTOR_1);
		assertThat(pending).isInstanceOf(LeaveResult.Pending.class);
		String id = ((LeaveResult.Pending) pending).request().id();

		assertThat(leaves.approve(id, DOCTOR_1)).isInstanceOf(LeaveResult.NotAllowed.class);
		assertThat(leaves.approve(id, ADMIN)).isInstanceOf(LeaveResult.Approved.class);
		assertThat(inputs.findByMonth(NOVEMBER).orElseThrow().leaves()).hasSize(1);

		EditResult edit = edits.applyEdit(new EditCommand.Assign(new ShiftSlot(first, StandardShifts.SHIFT_II), D01.id()), ADMIN);
		assertThat(edit).isInstanceOf(EditResult.Rejected.class);
		assertThat(((EditResult.Rejected) edit).violations()).extracting(Violation::constraintId)
				.containsExactly("no-leave-conflict");
		assertThat(rosters.findByMonth(NOVEMBER).orElseThrow().assignments()).isEmpty();
	}

	@Test
	void anEditThatNeedsApprovalIsAppliedWhenTheAdminApprovesIt() {
		LocalDate day = NOVEMBER.atDay(20);
		ChangeResult filed = changes.file(new EditCommand.Assign(new ShiftSlot(day, StandardShifts.SHIFT_I), D01.id(), Post.WARD),
				RECEPTION);
		assertThat(filed).isInstanceOf(ChangeResult.Pending.class);
		String id = ((ChangeResult.Pending) filed).request().id();
		assertThat(changeRequests.findOpen()).hasSize(1);

		assertThat(changes.approve(id, ADMIN)).isInstanceOf(ChangeResult.Approved.class);
		assertThat(rosters.findByMonth(NOVEMBER).orElseThrow().assignments())
				.containsExactly(new com.iitm.roster.domain.Assignment(new ShiftSlot(day, StandardShifts.SHIFT_I), D01, Post.WARD));
		assertThat(changeRequests.findOpen()).isEmpty();
	}

	@Test
	void rejectingALeaveLeavesTheMonthInputUntouched() {
		LeaveResult pending = leaves.propose(D01.id(), NOVEMBER.atDay(16), NOVEMBER.atDay(18), DOCTOR_1);
		String id = ((LeaveResult.Pending) pending).request().id();

		assertThat(leaves.reject(id, ADMIN)).isInstanceOf(LeaveResult.Rejected.class);
		assertThat(inputs.findByMonth(NOVEMBER).orElseThrow().leaves()).isEmpty();
		assertThat(leaveRequests.findOpen()).isEmpty();
	}

}
