package com.iitm.roster.requests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.iitm.roster.domain.Actor;
import com.iitm.roster.domain.Assignment;
import com.iitm.roster.domain.AuditEvent;
import com.iitm.roster.domain.HospitalCalendar;
import com.iitm.roster.domain.Leave;
import com.iitm.roster.domain.RequestState;
import com.iitm.roster.domain.Role;
import com.iitm.roster.domain.Roster;
import com.iitm.roster.domain.RosterStatus;
import com.iitm.roster.domain.Post;
import com.iitm.roster.domain.Staff;
import com.iitm.roster.domain.StaffRole;
import com.iitm.roster.domain.StandardShifts;
import com.iitm.roster.repo.AuditStore;
import com.iitm.roster.repo.LeaveRequestStore;
import com.iitm.roster.repo.MonthInputStore;
import com.iitm.roster.repo.RosterStore;
import com.iitm.roster.repo.StaffStore;
import com.iitm.roster.scheduler.HospitalRosterGenerator;
import com.iitm.roster.scheduler.MonthInput;
import com.iitm.roster.security.HospitalAuthorizationPolicy;
import com.iitm.roster.service.LeaveLedger;
import com.iitm.roster.service.RosterGenerationService;
import com.iitm.roster.service.RosterService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

/**
 * Approved leave reaches the roster whichever order things happen in: leave approved before
 * the month is generated, leave typed in when generating, generating again, and leave approved
 * on a draft or a published roster. Synthetic December 2026 with 11 made-up doctors; each
 * generation takes a few seconds.
 */
@DataJpaTest
@Import({RosterStore.class, StaffStore.class, AuditStore.class, MonthInputStore.class, LeaveRequestStore.class,
		LeaveLedger.class})
class LeaveReachesRosterTest {

	private static final YearMonth DECEMBER = YearMonth.of(2026, 12);
	private static final Instant NOW = Instant.parse("2026-11-07T05:30:00Z");
	private static final Actor ADMIN = new Actor("admin", Role.ADMIN, Optional.empty());
	private static final LocalDate FIRST = DECEMBER.atDay(15);
	private static final LocalDate LAST = DECEMBER.atDay(17);

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
	private LeaveLedger ledger;

	private final List<Staff> doctors = new ArrayList<>();
	private RosterGenerationService generation;
	private RosterService edits;
	private LeaveLifecycle leaves;

	@BeforeEach
	void setUp() {
		for (int i = 1; i <= 11; i++) {
			Staff doctor = new Staff(String.format("D%02d", i), "Test Doctor " + i, StaffRole.DOCTOR);
			doctors.add(doctor);
			staff.save(doctor);
		}
		Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
		HospitalRosterGenerator generator =
				new HospitalRosterGenerator(StandardShifts.SHIFT_I, StandardShifts.SHIFT_II, StandardShifts.NIGHT);
		generation = new RosterGenerationService(generator, staff, inputs, rosters, audit, leaveRequests, clock);
		edits = new RosterService(rosters, staff, audit, month -> inputs.findByMonth(month)
				.map(stored -> generator.constraints(new MonthInput(stored.month(), stored.doctors(),
						stored.firstOnNights(), stored.leaves(), stored.calendar(), stored.weekdayPerShift(),
						stored.weekendPerShift())))
				.orElse(List.of()), clock);
		leaves = new LeaveLifecycle(leaveRequests, staff, rosters, ledger, generation, edits,
				new HospitalAuthorizationPolicy(clock), clock);
	}

	@Test
	void leaveApprovedBeforeTheMonthIsGeneratedKeepsTheDoctorOffThoseDays() {
		Staff doctor = doctors.get(3);
		String id = propose(doctor);

		LeaveResult approved = leaves.approve(id, ADMIN);

		assertThat(approved).isInstanceOfSatisfying(LeaveResult.Approved.class, result -> {
			assertThat(result.regenerated()).isFalse();
			assertThat(result.freed()).isEmpty();
		});
		generation.generate(december(List.of()), ADMIN);
		assertThat(onLeaveDays(rosters.findByMonth(DECEMBER).orElseThrow(), doctor)).isEmpty();
		assertThat(inputs.findByMonth(DECEMBER).orElseThrow().leaves()).containsExactly(new Leave(doctor, FIRST, LAST));
	}

	@Test
	void generatingAgainWithoutTheLeaveKeepsLeaveGivenTheFirstTime() {
		Staff doctor = doctors.get(3);
		generation.generate(december(List.of(new Leave(doctor, FIRST, LAST))), ADMIN);

		generation.generate(december(List.of()), ADMIN);

		assertThat(onLeaveDays(rosters.findByMonth(DECEMBER).orElseThrow(), doctor)).isEmpty();
		assertThat(inputs.findByMonth(DECEMBER).orElseThrow().leaves()).containsExactly(new Leave(doctor, FIRST, LAST));
		assertThat(leaveRequests.findApproved(DECEMBER)).singleElement()
				.satisfies(request -> assertThat(request.state()).isEqualTo(RequestState.APPROVED));
	}

	@Test
	void approvingLeaveOnADraftGeneratesTheMonthAgainWithIt() {
		Staff doctor = doctors.get(3);
		generation.generate(december(List.of()), ADMIN);
		assertThat(onLeaveDays(rosters.findByMonth(DECEMBER).orElseThrow(), doctor)).isNotEmpty();
		String id = propose(doctor);

		LeaveResult approved = leaves.approve(id, ADMIN);

		assertThat(approved).isInstanceOfSatisfying(LeaveResult.Approved.class,
				result -> assertThat(result.regenerated()).isTrue());
		Roster after = rosters.findByMonth(DECEMBER).orElseThrow();
		assertThat(after.status()).isEqualTo(RosterStatus.DRAFT);
		assertThat(onLeaveDays(after, doctor)).isEmpty();
		assertThat(inputs.findByMonth(DECEMBER).orElseThrow().leaves()).containsExactly(new Leave(doctor, FIRST, LAST));
		assertThat(audit.findAll()).extracting(AuditEvent::summary).hasSize(2)
				.allSatisfy(summary -> assertThat(summary).startsWith("generated 2026-12"));
	}

	private Roster handEditedDraft(Staff notThisDoctor) {
		generation.generate(december(List.of()), ADMIN);
		Roster before = rosters.findByMonth(DECEMBER).orElseThrow();
		Assignment edited = before.assignments().stream()
				.filter(a -> a.post() == Post.GENERAL && !a.staff().equals(notThisDoctor) && a.slot().date().isAfter(LAST))
				.findFirst().orElseThrow();
		Roster after = before.withoutAssignment(edited);
		rosters.save(after);
		return after;
	}

	@Test
	void approvingLeaveOnAHandEditedDraftKeepsTheEditsAndOnlyFreesThatDoctor() {
		Staff doctor = doctors.get(3);
		Roster edited = handEditedDraft(doctor);
		int shiftsOnLeaveDays = onLeaveDays(edited, doctor).size();
		String id = propose(doctor);

		LeaveResult approved = leaves.approve(id, ADMIN);

		assertThat(approved).isInstanceOfSatisfying(LeaveResult.Approved.class, result -> {
			assertThat(result.regenerated()).isFalse();
			assertThat(result.freed()).hasSize(shiftsOnLeaveDays).isNotEmpty();
		});
		Roster after = rosters.findByMonth(DECEMBER).orElseThrow();
		assertThat(onLeaveDays(after, doctor)).isEmpty();
		assertThat(after.assignments()).hasSize(edited.assignments().size() - shiftsOnLeaveDays);
	}

	@Test
	void cancellingLeaveOnAnUntouchedDraftGeneratesItAgainWithoutTheLeave() {
		Staff doctor = doctors.get(3);
		generation.generate(december(List.of()), ADMIN);
		Roster original = rosters.findByMonth(DECEMBER).orElseThrow();
		String id = propose(doctor);
		leaves.approve(id, ADMIN);

		LeaveResult cancelled = leaves.cancel(id, ADMIN);

		assertThat(cancelled).isInstanceOfSatisfying(LeaveResult.Cancelled.class, result -> {
			assertThat(result.regenerated()).isTrue();
			assertThat(result.request().state()).isEqualTo(RequestState.CANCELLED);
		});
		assertThat(inputs.findByMonth(DECEMBER).orElseThrow().leaves()).isEmpty();
		assertThat(leaveRequests.findApproved(DECEMBER)).isEmpty();
		assertThat(rosters.findByMonth(DECEMBER).orElseThrow().assignments()).containsExactlyInAnyOrderElementsOf(original.assignments());
		assertThat(audit.findAll()).extracting(AuditEvent::summary).anyMatch(text -> text.startsWith("cancelled leave for D04"));
	}

	@Test
	void cancellingLeaveOnAHandEditedDraftLeavesTheRosterAlone() {
		Staff doctor = doctors.get(3);
		generation.generate(december(List.of(new Leave(doctor, FIRST, LAST))), ADMIN);
		String id = leaveRequests.findApproved(DECEMBER).get(0).id();
		Roster before = rosters.findByMonth(DECEMBER).orElseThrow();
		Assignment edited = before.assignments().stream().filter(a -> a.post() == Post.GENERAL).findFirst().orElseThrow();
		Roster handEdited = before.withoutAssignment(edited);
		rosters.save(handEdited);

		LeaveResult cancelled = leaves.cancel(id, ADMIN);

		assertThat(cancelled).isInstanceOfSatisfying(LeaveResult.Cancelled.class, result -> assertThat(result.regenerated()).isFalse());
		assertThat(rosters.findByMonth(DECEMBER).orElseThrow().assignments()).containsExactlyInAnyOrderElementsOf(handEdited.assignments());
		assertThat(inputs.findByMonth(DECEMBER).orElseThrow().leaves()).isEmpty();
	}

	@Test
	void onlyApprovedLeaveCanBeCancelledAndOnlyByAnAdmin() {
		Staff doctor = doctors.get(3);
		String id = propose(doctor);
		assertThat(leaves.cancel(id, ADMIN)).isInstanceOf(LeaveResult.Refused.class);

		leaves.approve(id, ADMIN);

		assertThat(leaves.cancel(id, new Actor("staff1", Role.STAFF, Optional.of("D01")))).isInstanceOf(LeaveResult.NotAllowed.class);
		assertThat(leaveRequests.findApproved(DECEMBER)).hasSize(1);
	}

	@Test
	void approvingLeaveOnAPublishedRosterTakesTheDoctorOffOnlyThoseShifts() {
		Staff doctor = doctors.get(3);
		generation.generate(december(List.of()), ADMIN);
		Roster published = edits.publish(DECEMBER, ADMIN);
		List<Assignment> clashing = onLeaveDays(published, doctor);
		assertThat(clashing).isNotEmpty();
		String id = propose(doctor);

		LeaveResult approved = leaves.approve(id, ADMIN);

		assertThat(approved).isInstanceOfSatisfying(LeaveResult.Approved.class, result -> {
			assertThat(result.regenerated()).isFalse();
			assertThat(result.freed()).containsExactlyInAnyOrderElementsOf(clashing);
		});
		Roster after = rosters.findByMonth(DECEMBER).orElseThrow();
		assertThat(after.status()).isEqualTo(RosterStatus.PUBLISHED);
		List<Assignment> expected = new ArrayList<>(published.assignments());
		expected.removeAll(clashing);
		assertThat(after.assignments()).containsExactlyInAnyOrderElementsOf(expected);
		assertThat(audit.findAll()).extracting(AuditEvent::summary)
				.filteredOn(summary -> summary.startsWith("freed "))
				.hasSize(clashing.size())
				.allSatisfy(summary -> assertThat(summary).endsWith("from D04: approved leave"));
	}

	@Test
	void leaveForSomeoneNotOnTheDoctorListIsRefusedAndNothingIsStored() {
		Staff stranger = new Staff("D99", "Test Doctor 99", StaffRole.DOCTOR);

		assertThatThrownBy(() -> generation.generate(december(List.of(new Leave(stranger, FIRST, LAST))), ADMIN))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("D99");
		assertThat(rosters.findByMonth(DECEMBER)).isEmpty();
		assertThat(inputs.findByMonth(DECEMBER)).isEmpty();
		assertThat(leaveRequests.findApproved(DECEMBER)).isEmpty();
	}

	@Test
	void leaveForADoctorNotOnTheGeneratedMonthIsRefusedAndStaysPending() {
		Staff absent = doctors.get(10);
		generation.generate(new MonthInput(DECEMBER, doctors.subList(0, 10), doctors.get(0), List.of(),
				HospitalCalendar.noHolidays(), 4, 2), ADMIN);
		Roster before = rosters.findByMonth(DECEMBER).orElseThrow();
		String id = propose(absent);

		LeaveResult result = leaves.approve(id, ADMIN);

		assertThat(result).isInstanceOfSatisfying(LeaveResult.Refused.class,
				refused -> assertThat(refused.reason()).contains("D11"));
		assertThat(leaveRequests.findById(id).orElseThrow().state()).isEqualTo(RequestState.PENDING);
		assertThat(rosters.findByMonth(DECEMBER)).contains(before);
	}

	private String propose(Staff doctor) {
		Actor self = new Actor("staff-" + doctor.id(), Role.STAFF, Optional.of(doctor.id()));
		LeaveResult pending = leaves.propose(doctor.id(), FIRST, LAST, self);
		assertThat(pending).isInstanceOf(LeaveResult.Pending.class);
		return ((LeaveResult.Pending) pending).request().id();
	}

	private MonthInput december(List<Leave> leave) {
		return new MonthInput(DECEMBER, doctors, doctors.get(0), leave, HospitalCalendar.noHolidays(), 4, 2);
	}

	private static List<Assignment> onLeaveDays(Roster roster, Staff doctor) {
		return roster.assignments().stream()
				.filter(assignment -> assignment.staff().id().equals(doctor.id()))
				.filter(assignment -> !assignment.slot().date().isBefore(FIRST) && !assignment.slot().date().isAfter(LAST))
				.toList();
	}

}
