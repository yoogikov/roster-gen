package com.iitm.roster.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import com.iitm.roster.constraints.Constraint;
import com.iitm.roster.constraints.MinRestGapConstraint;
import com.iitm.roster.constraints.MinStaffingConstraint;
import com.iitm.roster.constraints.NoLeaveConflictConstraint;
import com.iitm.roster.constraints.Severity;
import com.iitm.roster.constraints.StaffingRequirement;
import com.iitm.roster.domain.Assignment;
import com.iitm.roster.domain.Leave;
import com.iitm.roster.domain.Roster;
import com.iitm.roster.domain.ShiftSlot;
import com.iitm.roster.domain.ShiftType;
import com.iitm.roster.domain.Staff;
import com.iitm.roster.domain.StaffRole;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.IntSummaryStatistics;
import java.util.List;
import org.junit.jupiter.api.Test;

class GreedyRosterGeneratorTest {

	private static final ShiftType MORNING = new ShiftType("M", LocalTime.of(8, 0), Duration.ofHours(6));
	private static final ShiftType EVENING = new ShiftType("E", LocalTime.of(14, 0), Duration.ofHours(6));
	private static final ShiftType NIGHT = new ShiftType("N", LocalTime.of(20, 0), Duration.ofHours(12));

	private static final YearMonth JULY = YearMonth.of(2026, 7);

	/** One doctor on each of the three shifts, every day. */
	private static final List<StaffingRequirement> ONE_DOCTOR_PER_SHIFT = List.of(
			new StaffingRequirement(MORNING, StaffRole.DOCTOR, 1),
			new StaffingRequirement(EVENING, StaffRole.DOCTOR, 1),
			new StaffingRequirement(NIGHT, StaffRole.DOCTOR, 1));

	private final GreedyRosterGenerator generator = new GreedyRosterGenerator();

	private static List<Staff> doctors(int count) {
		List<Staff> doctors = new ArrayList<>();
		for (int i = 1; i <= count; i++) {
			doctors.add(new Staff(String.format("D%02d", i), "Test Doctor " + i, StaffRole.DOCTOR));
		}
		return doctors;
	}

	private static List<Constraint> julyRules(List<Leave> leaves) {
		return List.of(
				new MinStaffingConstraint(ONE_DOCTOR_PER_SHIFT, Severity.HARD),
				new NoLeaveConflictConstraint(leaves, Severity.HARD),
				new MinRestGapConstraint(Duration.ofHours(8), Severity.HARD));
	}

	private static GenerationRequest request(Roster start, List<Staff> staff, List<Constraint> constraints) {
		return new GenerationRequest(start, staff, OpenSlots.missing(start, ONE_DOCTOR_PER_SHIFT), constraints);
	}

	private static List<Assignment> violationsFree(Roster roster, List<Constraint> constraints) {
		for (Constraint constraint : constraints) {
			assertThat(constraint.check(roster)).as(constraint.id()).isEmpty();
		}
		return roster.assignments();
	}

	@Test
	void fillsTheSyntheticJulyWithNoHardViolations() {
		List<Staff> staff = doctors(8);
		List<Leave> leaves = List.of(
				new Leave(staff.get(0), LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 14)),
				new Leave(staff.get(1), LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 25)));
		List<Constraint> rules = julyRules(leaves);

		GenerationResult result = generator.generate(request(Roster.empty(JULY), staff, rules));

		assertThat(result).isInstanceOf(GenerationResult.Ok.class);
		Roster roster = ((GenerationResult.Ok) result).roster();
		assertThat(violationsFree(roster, rules)).hasSize(31 * 3);
	}

	@Test
	void spreadsShiftsEvenlyWhenNothingElseDecides() {
		List<Staff> staff = doctors(8);

		Roster roster = ((GenerationResult.Ok) generator.generate(request(Roster.empty(JULY), staff, julyRules(List.of())))).roster();

		IntSummaryStatistics load = staff.stream().mapToInt(s -> roster.assignmentsFor(s).size()).summaryStatistics();
		assertThat(load.getMax() - load.getMin()).isLessThanOrEqualTo(1);
	}

	@Test
	void tooFewDoctorsGivesInfeasibleNamingTheUnfilledSlotsInsteadOfThrowing() {
		// One doctor cannot do three shifts a day with 8 hours' rest.
		List<Constraint> rules = julyRules(List.of());

		GenerationResult result = generator.generate(request(Roster.empty(JULY), doctors(1), rules));

		assertThat(result).isInstanceOf(GenerationResult.Infeasible.class);
		GenerationResult.Infeasible infeasible = (GenerationResult.Infeasible) result;
		assertThat(infeasible.unfilled()).isNotEmpty();
		assertThat(infeasible.partial().assignments()).isNotEmpty();
		assertThat(infeasible.partial().assignments().size() + infeasible.unfilled().size()).isEqualTo(31 * 3);
		// What was filled still respects leave and rest; only staffing is short.
		assertThat(rules.get(2).check(infeasible.partial())).isEmpty();
	}

	@Test
	void keepsTheAssignmentsAlreadyInTheStartingRoster() {
		List<Staff> staff = doctors(8);
		Assignment fixed = new Assignment(new ShiftSlot(JULY.atDay(5), NIGHT), staff.get(7));
		Roster start = Roster.empty(JULY).withAssignment(fixed);

		Roster roster = ((GenerationResult.Ok) generator.generate(request(start, staff, julyRules(List.of())))).roster();

		assertThat(roster.assignments()).contains(fixed).hasSize(31 * 3);
	}

	@Test
	void doesNotPutADoctorIntoANurseSlot() {
		ShiftSlot slot = new ShiftSlot(JULY.atDay(1), MORNING);
		GenerationRequest request = new GenerationRequest(Roster.empty(JULY), doctors(3),
				List.of(new OpenSlot(slot, StaffRole.NURSE)), List.of());

		GenerationResult result = generator.generate(request);

		assertThat(result).isEqualTo(new GenerationResult.Infeasible(Roster.empty(JULY), List.of(slot)));
	}

	@Test
	void nothingToFillReturnsTheStartingRoster() {
		GenerationRequest request = new GenerationRequest(Roster.empty(JULY), doctors(2), List.of(), List.of());

		assertThat(generator.generate(request)).isEqualTo(new GenerationResult.Ok(Roster.empty(JULY), 0));
	}

	@Test
	void fillsTheHardestSlotFirst() {
		// Only D01 can work the 2nd's morning (D02 is on leave). Filled in date order, D01
		// would take the 1st's night first (lower id wins the tie) and then have no rest
		// before that morning. Hardest first, D01 takes the morning and D02 the night.
		List<Staff> staff = doctors(2);
		ShiftSlot nightOfFirst = new ShiftSlot(JULY.atDay(1), NIGHT);
		ShiftSlot morningOfSecond = new ShiftSlot(JULY.atDay(2), MORNING);
		List<Constraint> rules = List.of(
				new NoLeaveConflictConstraint(List.of(new Leave(staff.get(1), JULY.atDay(2), JULY.atDay(2))), Severity.HARD),
				new MinRestGapConstraint(Duration.ofHours(8), Severity.HARD));
		GenerationRequest request = new GenerationRequest(Roster.empty(JULY), staff,
				List.of(new OpenSlot(nightOfFirst, StaffRole.DOCTOR), new OpenSlot(morningOfSecond, StaffRole.DOCTOR)), rules);

		GenerationResult result = generator.generate(request);

		assertThat(result).isInstanceOf(GenerationResult.Ok.class);
		assertThat(((GenerationResult.Ok) result).roster().assignments()).containsExactlyInAnyOrder(
				new Assignment(morningOfSecond, staff.get(0)), new Assignment(nightOfFirst, staff.get(1)));
	}

	@Test
	void twoPeopleNeededOnOneShiftAreTwoDifferentPeople() {
		ShiftSlot slot = new ShiftSlot(JULY.atDay(1), MORNING);
		OpenSlot open = new OpenSlot(slot, StaffRole.DOCTOR);
		GenerationRequest request = new GenerationRequest(Roster.empty(JULY), doctors(3), List.of(open, open), List.of());

		Roster roster = ((GenerationResult.Ok) generator.generate(request)).roster();

		assertThat(roster.assignmentsOn(slot)).extracting(Assignment::staff).doesNotHaveDuplicates().hasSize(2);
	}

	@Test
	void sameInputGivesTheSameRoster() {
		GenerationRequest request = request(Roster.empty(JULY), doctors(6), julyRules(List.of()));

		assertThat(generator.generate(request)).isEqualTo(generator.generate(request));
	}

}
