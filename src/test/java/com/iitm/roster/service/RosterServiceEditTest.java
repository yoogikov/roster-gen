package com.iitm.roster.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.iitm.roster.constraints.Constraint;
import com.iitm.roster.constraints.Severity;
import com.iitm.roster.constraints.Violation;
import com.iitm.roster.domain.Actor;
import com.iitm.roster.domain.Assignment;
import com.iitm.roster.domain.AuditEvent;
import com.iitm.roster.domain.Post;
import com.iitm.roster.domain.Role;
import com.iitm.roster.domain.Roster;
import com.iitm.roster.domain.RosterStatus;
import com.iitm.roster.domain.ShiftSlot;
import com.iitm.roster.domain.ShiftType;
import com.iitm.roster.domain.Staff;
import com.iitm.roster.domain.StaffRole;
import com.iitm.roster.repo.AuditStore;
import com.iitm.roster.repo.RosterStore;
import com.iitm.roster.repo.StaffStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
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
 * {@code applyEdit} against the real stores on H2. The service is built by hand so each
 * test can choose its constraints; transaction rollback is covered by
 * {@link RosterServiceTransactionTest}.
 */
@DataJpaTest
@Import({RosterStore.class, StaffStore.class, AuditStore.class})
class RosterServiceEditTest {

	private static final YearMonth JULY = YearMonth.of(2026, 7);
	private static final LocalDate DAY = JULY.atDay(10);
	private static final Instant NOW = Instant.parse("2026-07-01T04:30:00Z");

	private static final ShiftType MORNING = new ShiftType("M", LocalTime.of(8, 0), Duration.ofHours(6));
	private static final ShiftType EARLY = new ShiftType("X", LocalTime.of(6, 0), Duration.ofHours(8));
	private static final ShiftType NIGHT = new ShiftType("N", LocalTime.of(20, 0), Duration.ofHours(12));

	private static final Staff DOC_A = new Staff("S01", "Test Doctor A", StaffRole.DOCTOR);
	private static final Staff DOC_B = new Staff("S02", "Test Doctor B", StaffRole.DOCTOR);
	private static final Staff NURSE_C = new Staff("S03", "Test Nurse C", StaffRole.NURSE);

	private static final Actor ADMIN = new Actor("admin", Role.ADMIN, Optional.empty());

	@Autowired
	private RosterStore rosters;

	@Autowired
	private StaffStore staff;

	@Autowired
	private AuditStore audit;

	private final List<Constraint> active = new ArrayList<>();

	private RosterService service;

	@BeforeEach
	void setUp() {
		staff.save(DOC_A);
		staff.save(DOC_B);
		staff.save(NURSE_C);
		service = new RosterService(rosters, staff, audit, month -> active, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void validAssignIsSavedWithExactlyOneAuditEvent() {
		saveRoster();

		EditResult result = service.applyEdit(new EditCommand.Assign(slot(DAY, MORNING), "S01"), ADMIN);

		Roster expected = new Roster(JULY, RosterStatus.DRAFT, List.of(on(DAY, MORNING, DOC_A)));
		assertThat(result).isEqualTo(new EditResult.Applied(expected, List.of()));
		assertThat(rosters.findByMonth(JULY)).contains(expected);
		assertThat(audit.findAll()).containsExactly(
				new AuditEvent(NOW, "admin", JULY, "assigned S01 to M (GENERAL) on 2026-07-10"));
	}

	@Test
	void doubleBookingAcrossMidnightIsRejectedAndNothingIsStored() {
		Roster start = saveRoster(on(DAY, NIGHT, DOC_A));

		EditResult result = service.applyEdit(new EditCommand.Assign(slot(DAY.plusDays(1), EARLY), "S01"), ADMIN);

		assertThat(result).isInstanceOfSatisfying(EditResult.Rejected.class, rejected ->
				assertThat(rejected.violations()).singleElement().satisfies(v -> {
					assertThat(v.constraintId()).isEqualTo(RosterService.DOUBLE_BOOKING);
					assertThat(v.severity()).isEqualTo(Severity.HARD);
					assertThat(v.date()).isEqualTo(DAY.plusDays(1));
				}));
		assertNothingStoredBeyond(start);
	}

	@Test
	void assigningTheSameSlotTwiceIsDoubleBooking() {
		Roster start = saveRoster(on(DAY, MORNING, DOC_A));

		EditResult result = service.applyEdit(new EditCommand.Assign(slot(DAY, MORNING), "S01"), ADMIN);

		assertThat(result).isInstanceOf(EditResult.Rejected.class);
		assertNothingStoredBeyond(start);
	}

	@Test
	void newHardViolationIsRejectedAndNothingIsStored() {
		Roster start = saveRoster();
		active.add(FakeConstraints.offOn(DOC_A, DAY));

		EditResult result = service.applyEdit(new EditCommand.Assign(slot(DAY, MORNING), "S01"), ADMIN);

		assertThat(result).isEqualTo(new EditResult.Rejected(List.of(
				new Violation("off-on", Severity.HARD, DAY, "Test Doctor A is off"))));
		assertNothingStoredBeyond(start);
	}

	@Test
	void editThatImprovesAnExistingHardViolationIsAllowed() {
		saveRoster();
		active.add(FakeConstraints.minimumOn(DAY, MORNING, 2));

		EditResult result = service.applyEdit(new EditCommand.Assign(slot(DAY, MORNING), "S01"), ADMIN);

		assertThat(result).isInstanceOf(EditResult.Applied.class);
		assertThat(audit.findAll()).hasSize(1);
	}

	@Test
	void fixingOneRuleDoesNotHideBreakingAnother() {
		Roster start = saveRoster();
		active.add(FakeConstraints.minimumOn(DAY, MORNING, 1));
		active.add(FakeConstraints.offOn(DOC_A, DAY));

		EditResult result = service.applyEdit(new EditCommand.Assign(slot(DAY, MORNING), "S01"), ADMIN);

		assertThat(result).isInstanceOfSatisfying(EditResult.Rejected.class, rejected ->
				assertThat(rejected.violations()).extracting(Violation::constraintId).containsExactly("off-on"));
		assertNothingStoredBeyond(start);
	}

	@Test
	void newSoftViolationIsReturnedAsWarningAndEditIsSaved() {
		saveRoster(on(DAY, NIGHT, DOC_B));
		active.add(FakeConstraints.avoid(NIGHT));

		EditResult result = service.applyEdit(new EditCommand.Assign(slot(DAY, NIGHT), "S03"), ADMIN);

		assertThat(result).isInstanceOfSatisfying(EditResult.Applied.class, applied ->
				assertThat(applied.warnings()).singleElement()
						.extracting(Violation::constraintId).isEqualTo("avoid"));
		assertThat(rosters.findByMonth(JULY).orElseThrow().assignmentsFor(NURSE_C)).hasSize(1);
	}

	@Test
	void unassignRemovesTheAssignmentWithOneAuditEvent() {
		saveRoster(on(DAY, MORNING, DOC_A), on(DAY, MORNING, DOC_B));

		service.applyEdit(new EditCommand.Unassign(slot(DAY, MORNING), "S01"), ADMIN);

		assertThat(rosters.findByMonth(JULY).orElseThrow().assignments()).containsExactly(on(DAY, MORNING, DOC_B));
		assertThat(audit.findAll()).extracting(AuditEvent::summary).containsExactly("removed S01 from M (GENERAL) on 2026-07-10");
	}

	@Test
	void moveHandsTheSlotOverWithOneAuditEvent() {
		saveRoster(on(DAY, MORNING, DOC_A));

		service.applyEdit(new EditCommand.Move(slot(DAY, MORNING), "S01", "S02"), ADMIN);

		assertThat(rosters.findByMonth(JULY).orElseThrow().assignments()).containsExactly(on(DAY, MORNING, DOC_B));
		assertThat(audit.findAll()).extracting(AuditEvent::summary)
				.containsExactly("moved M (GENERAL) on 2026-07-10 from S01 to S02");
	}

	@Test
	void moveToSomeoneAlreadyOnAnOverlappingShiftIsRejected() {
		Roster start = saveRoster(on(DAY, MORNING, DOC_A), on(DAY, EARLY, DOC_B));

		EditResult result = service.applyEdit(new EditCommand.Move(slot(DAY, MORNING), "S01", "S02"), ADMIN);

		assertThat(result).isInstanceOf(EditResult.Rejected.class);
		assertNothingStoredBeyond(start);
	}

	@Test
	void unassigningSomeoneNotOnTheSlotThrowsAndStoresNothing() {
		Roster start = saveRoster(on(DAY, MORNING, DOC_A));

		assertThatThrownBy(() -> service.applyEdit(new EditCommand.Unassign(slot(DAY, MORNING), "S02"), ADMIN))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("S02 is not on");
		assertNothingStoredBeyond(start);
	}

	@Test
	void unknownStaffThrows() {
		saveRoster();

		assertThatThrownBy(() -> service.applyEdit(new EditCommand.Assign(slot(DAY, MORNING), "S99"), ADMIN))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("unknown staff: S99");
	}

	@Test
	void monthWithoutARosterThrows() {
		assertThatThrownBy(() -> service.applyEdit(new EditCommand.Assign(slot(DAY, MORNING), "S01"), ADMIN))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("no roster for 2026-07");
	}

	@Test
	void assignInAPostStoresThePost() {
		saveRoster();

		service.applyEdit(new EditCommand.Assign(slot(DAY, MORNING), "S01", Post.WARD), ADMIN);

		assertThat(rosters.findByMonth(JULY).orElseThrow().assignments())
				.containsExactly(new Assignment(slot(DAY, MORNING), DOC_A, Post.WARD));
		assertThat(audit.findAll()).extracting(AuditEvent::summary)
				.containsExactly("assigned S01 to M (WARD) on 2026-07-10");
	}

	@Test
	void moveKeepsThePost() {
		saveRoster(new Assignment(slot(DAY, MORNING), DOC_A, Post.CASUALTY));

		service.applyEdit(new EditCommand.Move(slot(DAY, MORNING), "S01", "S02"), ADMIN);

		assertThat(rosters.findByMonth(JULY).orElseThrow().assignments())
				.containsExactly(new Assignment(slot(DAY, MORNING), DOC_B, Post.CASUALTY));
	}

	@Test
	void unassignFindsTheAssignmentWhateverItsPost() {
		saveRoster(new Assignment(slot(DAY, MORNING), DOC_A, Post.WARD));

		service.applyEdit(new EditCommand.Unassign(slot(DAY, MORNING), "S01"), ADMIN);

		assertThat(rosters.findByMonth(JULY).orElseThrow().assignments()).isEmpty();
		assertThat(audit.findAll()).extracting(AuditEvent::summary)
				.containsExactly("removed S01 from M (WARD) on 2026-07-10");
	}

	@Test
	void moveToTheSamePersonIsRefusedWhenTheCommandIsBuilt() {
		assertThatThrownBy(() -> new EditCommand.Move(slot(DAY, MORNING), "S01", "S01"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	private Roster saveRoster(Assignment... assignments) {
		Roster roster = new Roster(JULY, RosterStatus.DRAFT, List.of(assignments));
		rosters.save(roster);
		return roster;
	}

	private void assertNothingStoredBeyond(Roster start) {
		assertThat(rosters.findByMonth(JULY)).contains(start);
		assertThat(audit.findAll()).isEmpty();
	}

	private static ShiftSlot slot(LocalDate date, ShiftType shift) {
		return new ShiftSlot(date, shift);
	}

	private static Assignment on(LocalDate date, ShiftType shift, Staff member) {
		return new Assignment(slot(date, shift), member);
	}

}
