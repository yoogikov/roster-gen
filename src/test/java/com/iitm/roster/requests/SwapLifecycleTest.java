package com.iitm.roster.requests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.iitm.roster.constraints.NightIncludesMorningWardConstraint;
import com.iitm.roster.constraints.PostDutyOffConstraint;
import com.iitm.roster.constraints.Severity;
import com.iitm.roster.constraints.Violation;
import com.iitm.roster.domain.Actor;
import com.iitm.roster.domain.Assignment;
import com.iitm.roster.domain.HospitalCalendar;
import com.iitm.roster.domain.Leave;
import com.iitm.roster.domain.Post;
import com.iitm.roster.domain.RequestState;
import com.iitm.roster.domain.Role;
import com.iitm.roster.domain.Roster;
import com.iitm.roster.domain.RosterStatus;
import com.iitm.roster.domain.ShiftSlot;
import com.iitm.roster.domain.Staff;
import com.iitm.roster.domain.StaffRole;
import com.iitm.roster.domain.StandardShifts;
import com.iitm.roster.domain.SwapRequest;
import com.iitm.roster.repo.AuditStore;
import com.iitm.roster.repo.MonthInputStore;
import com.iitm.roster.repo.RosterStore;
import com.iitm.roster.repo.StaffStore;
import com.iitm.roster.repo.StoredMonthInput;
import com.iitm.roster.repo.SwapRequestStore;
import com.iitm.roster.scheduler.NightRotation;
import com.iitm.roster.security.HospitalAuthorizationPolicy;
import com.iitm.roster.service.RosterService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

/**
 * The swap state machine against the real stores. "Now" is 11:00 IST on 4 Nov 2026, so a
 * shift later in November has not started and is not a past date.
 */
@DataJpaTest
@Import({RosterStore.class, StaffStore.class, AuditStore.class, SwapRequestStore.class, MonthInputStore.class})
class SwapLifecycleTest {

	private static final YearMonth NOVEMBER = YearMonth.of(2026, 11);
	private static final Instant NOW = Instant.parse("2026-11-04T05:30:00Z");

	private static final Staff D01 = new Staff("D01", "Test Doctor 1", StaffRole.DOCTOR);
	private static final Staff D02 = new Staff("D02", "Test Doctor 2", StaffRole.DOCTOR);
	private static final Staff D03 = new Staff("D03", "Test Doctor 3", StaffRole.DOCTOR);

	private static final Actor ADMIN = new Actor("admin", Role.ADMIN, Optional.empty());
	private static final Actor DOCTOR_1 = new Actor("staff1", Role.STAFF, Optional.of("D01"));
	private static final Actor DOCTOR_2 = new Actor("staff2", Role.STAFF, Optional.of("D02"));

	@Autowired
	private RosterStore rosters;

	@Autowired
	private StaffStore staff;

	@Autowired
	private AuditStore audit;

	@Autowired
	private SwapRequestStore requests;

	@Autowired
	private MonthInputStore inputs;

	private final List<com.iitm.roster.constraints.Constraint> active = new ArrayList<>();

	private SwapLifecycle swaps;

	@BeforeEach
	void setUp() {
		staff.save(D01);
		staff.save(D02);
		staff.save(D03);
		swaps = lifecycle(Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void proposeAcceptAndApproveMovesBothAssignmentsAndWritesTwoAuditEvents() {
		Assignment offered = shift(NOVEMBER.atDay(20), D01);
		Assignment wanted = shift(NOVEMBER.atDay(22), D02);
		save(offered, wanted);

		SwapRequest pending = pending(swaps.propose(offered, wanted, DOCTOR_1));
		SwapRequest accepted = accepted(swaps.accept(pending.id(), DOCTOR_2));
		SwapResult approved = swaps.approve(accepted.id(), ADMIN);

		assertThat(approved).isEqualTo(new SwapResult.Approved(accepted.withState(RequestState.APPROVED)));
		assertThat(rosters.findByMonth(NOVEMBER).orElseThrow().assignments()).containsExactlyInAnyOrder(
				shift(NOVEMBER.atDay(20), D02), shift(NOVEMBER.atDay(22), D01));
		assertThat(audit.findByMonth(NOVEMBER)).hasSize(2);
		assertThat(audit.findByMonth(NOVEMBER)).allSatisfy(event -> assertThat(event.actor()).isEqualTo("admin"));
	}

	@Test
	void approvingANightAlsoMovesThatDaysWardDuty() {
		active.add(new NightIncludesMorningWardConstraint(StandardShifts.SHIFT_I, StandardShifts.NIGHT, Severity.HARD));
		active.add(new PostDutyOffConstraint(StandardShifts.NIGHT, Severity.HARD));
		LocalDate night = NOVEMBER.atDay(20);
		Assignment nightDuty = new Assignment(slot(night, StandardShifts.NIGHT), D01, Post.GENERAL);
		Assignment ward = new Assignment(slot(night, StandardShifts.SHIFT_I), D01, Post.WARD);
		Assignment afternoon = shift(NOVEMBER.atDay(22), D02);
		save(nightDuty, ward, afternoon);

		String id = acceptedId(nightDuty, afternoon);

		assertThat(swaps.approve(id, ADMIN)).isInstanceOf(SwapResult.Approved.class);
		assertThat(rosters.findByMonth(NOVEMBER).orElseThrow().assignments()).containsExactlyInAnyOrder(
				new Assignment(slot(night, StandardShifts.NIGHT), D02, Post.GENERAL),
				new Assignment(slot(night, StandardShifts.SHIFT_I), D02, Post.WARD),
				shift(NOVEMBER.atDay(22), D01));
		// The ward move is its own edit, so a night produces one more audit event than the two shifts.
		assertThat(audit.findByMonth(NOVEMBER)).hasSize(3);
	}

	@Test
	void nightSwapIsRejectedWhenTheNewNightDoctorWorksTheNextDay() {
		active.add(new NightIncludesMorningWardConstraint(StandardShifts.SHIFT_I, StandardShifts.NIGHT, Severity.HARD));
		active.add(new PostDutyOffConstraint(StandardShifts.NIGHT, Severity.HARD));
		LocalDate night = NOVEMBER.atDay(20);
		Assignment nightDuty = new Assignment(slot(night, StandardShifts.NIGHT), D01, Post.GENERAL);
		Assignment ward = new Assignment(slot(night, StandardShifts.SHIFT_I), D01, Post.WARD);
		Assignment nextDay = shift(night.plusDays(1), D02);
		Assignment offeredFor = shift(NOVEMBER.atDay(23), D02);
		save(nightDuty, ward, nextDay, offeredFor);
		String id = acceptedId(nightDuty, offeredFor);

		SwapResult result = swaps.approve(id, ADMIN);

		assertThat(result).isInstanceOf(SwapResult.EditRejected.class);
		assertThat(((SwapResult.EditRejected) result).edit().violations())
				.extracting(Violation::constraintId)
				.contains(PostDutyOffConstraint.ID);
		assertThat(requests.findById(id).orElseThrow().state()).isEqualTo(RequestState.ACCEPTED);
		assertThat(rosters.findByMonth(NOVEMBER).orElseThrow().assignments())
				.containsExactlyInAnyOrder(nightDuty, ward, nextDay, offeredFor);
		assertThat(audit.findAll()).isEmpty();
	}

	@Test
	void approvingANightWithoutWardDutyThrowsAndStaysAccepted() {
		Assignment nightDuty = new Assignment(slot(NOVEMBER.atDay(20), StandardShifts.NIGHT), D01, Post.GENERAL);
		Assignment afternoon = shift(NOVEMBER.atDay(22), D02);
		save(nightDuty, afternoon);
		String id = acceptedId(nightDuty, afternoon);

		assertThatThrownBy(() -> swaps.approve(id, ADMIN))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("no Shift I duty");
		assertThat(requests.findById(id).orElseThrow().state()).isEqualTo(RequestState.ACCEPTED);
		assertThat(audit.findAll()).isEmpty();
	}

	@Test
	void rejectLeavesTheRosterAndWritesNoAuditEvent() {
		Assignment offered = shift(NOVEMBER.atDay(20), D01);
		Assignment wanted = shift(NOVEMBER.atDay(22), D02);
		save(offered, wanted);
		SwapRequest pending = pending(swaps.propose(offered, wanted, DOCTOR_1));

		SwapResult result = swaps.reject(pending.id(), ADMIN);

		assertThat(result).isEqualTo(new SwapResult.Rejected(pending.withState(RequestState.REJECTED)));
		assertThat(rosters.findByMonth(NOVEMBER).orElseThrow().assignments()).containsExactly(offered, wanted);
		assertThat(audit.findAll()).isEmpty();
	}

	@Test
	void openRequestLapsesAtTheEarlierShiftStartAndTheRosterIsUntouched() {
		Assignment early = new Assignment(slot(NOVEMBER.atDay(20), StandardShifts.SHIFT_I), D02, Post.GENERAL);
		Assignment later = shift(NOVEMBER.atDay(25), D01);
		Assignment untouchedA = shift(NOVEMBER.atDay(27), D02);
		Assignment untouchedB = shift(NOVEMBER.atDay(28), D03);
		Roster start = save(early, later, untouchedA, untouchedB);
		SwapRequest due = pending(swaps.propose(later, early, DOCTOR_1));
		SwapRequest waiting = pending(swaps.propose(untouchedA, untouchedB, DOCTOR_2));

		assertThat(lifecycle(clockAt(early.slot())).lapseOpen()).isEmpty();
		assertThat(requests.findById(due.id()).orElseThrow().state()).isEqualTo(RequestState.PENDING);

		List<SwapRequest> lapsed = lifecycle(Clock.fixed(early.slot().start().toInstant(), ZoneOffset.UTC)).lapseOpen();

		assertThat(lapsed).containsExactly(due.withState(RequestState.LAPSED));
		assertThat(requests.findById(waiting.id()).orElseThrow().state()).isEqualTo(RequestState.PENDING);
		assertThat(rosters.findByMonth(NOVEMBER)).contains(start);
		assertThat(audit.findAll()).isEmpty();
	}

	@Test
	void anAcceptedRequestAlsoLapsesAndAnApprovedOneDoesNot() {
		Assignment offered = shift(NOVEMBER.atDay(20), D01);
		Assignment wanted = shift(NOVEMBER.atDay(22), D02);
		save(offered, wanted);
		String id = acceptedId(offered, wanted);
		Instant start = offered.slot().start().toInstant();

		assertThat(lifecycle(Clock.fixed(start, ZoneOffset.UTC)).lapseOpen())
				.extracting(SwapRequest::id)
				.containsExactly(id);
		assertThat(requests.findById(id).orElseThrow().state()).isEqualTo(RequestState.LAPSED);

		Assignment other = shift(NOVEMBER.atDay(24), D01);
		Assignment third = shift(NOVEMBER.atDay(26), D02);
		save(other, third);
		String approvedId = acceptedId(other, third);
		swaps.approve(approvedId, ADMIN);

		assertThat(lifecycle(Clock.fixed(other.slot().start().toInstant(), ZoneOffset.UTC)).lapseOpen()).isEmpty();
		assertThat(requests.findById(approvedId).orElseThrow().state()).isEqualTo(RequestState.APPROVED);
	}

	@Test
	void approveBeforeAcceptThrows() {
		Assignment offered = shift(NOVEMBER.atDay(20), D01);
		Assignment wanted = shift(NOVEMBER.atDay(22), D02);
		save(offered, wanted);
		SwapRequest pending = pending(swaps.propose(offered, wanted, DOCTOR_1));

		assertThatThrownBy(() -> swaps.approve(pending.id(), ADMIN))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("ACCEPTED");
		assertThat(requests.findById(pending.id()).orElseThrow().state()).isEqualTo(RequestState.PENDING);
	}

	@Test
	void someoneElseCannotAcceptAndStaffCannotOfferAnotherDoctorsShift() {
		Assignment offered = shift(NOVEMBER.atDay(20), D01);
		Assignment wanted = shift(NOVEMBER.atDay(22), D02);
		save(offered, wanted);

		assertThat(swaps.propose(wanted, offered, DOCTOR_1)).isInstanceOf(SwapResult.NotAllowed.class);
		assertThat(requests.findOpen()).isEmpty();

		SwapRequest pending = pending(swaps.propose(offered, wanted, DOCTOR_1));
		assertThat(swaps.accept(pending.id(), DOCTOR_1)).isInstanceOf(SwapResult.NotAllowed.class);
		assertThat(requests.findById(pending.id()).orElseThrow().state()).isEqualTo(RequestState.PENDING);
	}

	@Test
	void aDraftRosterCannotBeSwapped() {
		Assignment offered = shift(NOVEMBER.atDay(20), D01);
		Assignment wanted = shift(NOVEMBER.atDay(22), D02);
		rosters.save(new Roster(NOVEMBER, RosterStatus.DRAFT, List.of(offered, wanted)));

		assertThat(swaps.propose(offered, wanted, ADMIN)).isInstanceOf(SwapResult.NotAllowed.class);
		assertThat(requests.findOpen()).isEmpty();
	}

	@Test
	void aSwapThatDoubleBooksIsNotApplied() {
		Assignment offered = shift(NOVEMBER.atDay(20), D01);
		Assignment blocking = shift(NOVEMBER.atDay(22), D01);
		Assignment wanted = new Assignment(slot(NOVEMBER.atDay(22), StandardShifts.SHIFT_I), D02, Post.GENERAL);
		save(offered, blocking, wanted);
		String id = acceptedId(offered, wanted);

		SwapResult result = swaps.approve(id, ADMIN);

		assertThat(result).isInstanceOf(SwapResult.EditRejected.class);
		assertThat(requests.findById(id).orElseThrow().state()).isEqualTo(RequestState.ACCEPTED);
		assertThat(rosters.findByMonth(NOVEMBER).orElseThrow().assignments())
				.containsExactlyInAnyOrder(offered, blocking, wanted);
		assertThat(audit.findAll()).isEmpty();
	}

	@Test
	void aShortLeaveNightGoesToTheNextDoctorInTheRotation() {
		LocalDate from = NOVEMBER.atDay(10);
		LocalDate to = NOVEMBER.atDay(19);
		List<Leave> leaves = List.of(new Leave(D01, from, to));
		assertThat(ChronoUnit.DAYS.between(from, to) + 1).isEqualTo(NightRotation.LONG_LEAVE_DAYS);
		inputs.save(new StoredMonthInput(NOVEMBER, List.of(D01, D02, D03), D01, leaves, HospitalCalendar.noHolidays(), 4, 2));
		Map<LocalDate, Staff> planned = NightRotation.plan(NOVEMBER, List.of(D01, D02, D03), D01, leaves);

		LocalDate night = null;
		for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
			Staff doctor = planned.get(day);
			if (doctor != null && !doctor.equals(D01)) {
				night = day;
				break;
			}
		}
		assertThat(night).isNotNull();
		Staff expected = planned.get(night);
		Staff other = expected.equals(D02) ? D03 : D02;
		Assignment nightDuty = new Assignment(slot(night, StandardShifts.NIGHT), D01, Post.GENERAL);
		Assignment ward = new Assignment(slot(night, StandardShifts.SHIFT_I), D01, Post.WARD);
		Assignment expectedShift = shift(NOVEMBER.atDay(27), expected);
		Assignment otherShift = shift(NOVEMBER.atDay(28), other);
		save(nightDuty, ward, expectedShift, otherShift);

		SwapResult refused = swaps.propose(nightDuty, otherShift, ADMIN);
		assertThat(refused).isInstanceOf(SwapResult.Refused.class);
		assertThat(((SwapResult.Refused) refused).reason()).contains(expected.id());
		assertThat(requests.findOpen()).isEmpty();

		assertThat(swaps.propose(nightDuty, expectedShift, ADMIN)).isInstanceOf(SwapResult.Pending.class);
	}

	@Test
	void aLongLeaveNightIsNotForcedOntoTheRotation() {
		List<Leave> leaves = List.of(new Leave(D01, NOVEMBER.atDay(10), NOVEMBER.atDay(20)));
		List<Staff> order = List.of(D01, D02, D03);
		inputs.save(new StoredMonthInput(NOVEMBER, order, D01, leaves, HospitalCalendar.noHolidays(), 4, 2));
		LocalDate night = NOVEMBER.atDay(19);
		Staff planned = NightRotation.plan(NOVEMBER, order, D01, leaves).get(night);
		Staff other = D03.equals(planned) ? D02 : D03;
		Assignment nightDuty = new Assignment(slot(night, StandardShifts.NIGHT), D01, Post.GENERAL);
		Assignment ward = new Assignment(slot(night, StandardShifts.SHIFT_I), D01, Post.WARD);
		Assignment otherShift = shift(NOVEMBER.atDay(27), other);
		save(nightDuty, ward, otherShift);

		assertThat(swaps.propose(nightDuty, otherShift, ADMIN)).isInstanceOf(SwapResult.Pending.class);
	}

	@Test
	void assignmentsMissingFromTheRosterAreRejected() {
		Assignment offered = shift(NOVEMBER.atDay(20), D01);
		Assignment wanted = shift(NOVEMBER.atDay(22), D02);
		save(offered);

		assertThatThrownBy(() -> swaps.propose(offered, wanted, ADMIN))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("on the roster");
	}

	private SwapLifecycle lifecycle(Clock clock) {
		RosterService edits = new RosterService(rosters, staff, audit, month -> active, clock);
		return new SwapLifecycle(edits, rosters, requests, inputs, new HospitalAuthorizationPolicy(clock), clock);
	}

	private String acceptedId(Assignment offered, Assignment wanted) {
		SwapRequest pending = pending(swaps.propose(offered, wanted, DOCTOR_1));
		return accepted(swaps.accept(pending.id(), DOCTOR_2)).id();
	}

	/** One second before {@code slot} starts. */
	private static Clock clockAt(ShiftSlot slot) {
		return Clock.fixed(slot.start().toInstant().minus(java.time.Duration.ofSeconds(1)), ZoneOffset.UTC);
	}

	private Roster save(Assignment... assignments) {
		Roster roster = new Roster(NOVEMBER, RosterStatus.PUBLISHED, List.of(assignments));
		rosters.save(roster);
		return roster;
	}

	private static SwapRequest pending(SwapResult result) {
		assertThat(result).isInstanceOf(SwapResult.Pending.class);
		return ((SwapResult.Pending) result).request();
	}

	private static SwapRequest accepted(SwapResult result) {
		assertThat(result).isInstanceOf(SwapResult.Accepted.class);
		return ((SwapResult.Accepted) result).request();
	}

	private static Assignment shift(LocalDate date, Staff doctor) {
		return new Assignment(slot(date, StandardShifts.SHIFT_II), doctor, Post.GENERAL);
	}

	private static ShiftSlot slot(LocalDate date, com.iitm.roster.domain.ShiftType shift) {
		return new ShiftSlot(date, shift);
	}

}
