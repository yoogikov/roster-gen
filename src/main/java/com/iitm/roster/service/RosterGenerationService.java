package com.iitm.roster.service;

import com.iitm.roster.constraints.Constraint;
import com.iitm.roster.constraints.Severity;
import com.iitm.roster.constraints.Violation;
import com.iitm.roster.domain.Actor;
import com.iitm.roster.domain.AuditEvent;
import com.iitm.roster.domain.HospitalCalendar;
import com.iitm.roster.domain.Leave;
import com.iitm.roster.domain.LeaveRequest;
import com.iitm.roster.domain.RequestState;
import com.iitm.roster.domain.Roster;
import com.iitm.roster.domain.RosterStatus;
import com.iitm.roster.domain.Staff;
import com.iitm.roster.repo.AuditStore;
import com.iitm.roster.repo.LeaveRequestStore;
import com.iitm.roster.repo.MonthInputStore;
import com.iitm.roster.repo.RosterStore;
import com.iitm.roster.repo.StaffStore;
import com.iitm.roster.repo.StoredMonthInput;
import com.iitm.roster.scheduler.GenerationResult;
import com.iitm.roster.scheduler.HospitalRosterGenerator;
import com.iitm.roster.scheduler.MonthInput;
import com.iitm.roster.scheduler.NightRotation;
import com.iitm.roster.scheduler.OwedOffs;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Clock;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generates a month's roster from a doctor list and the hospital rules, and stores it
 * together with the input, so later edits are checked against the same rules.
 *
 * <p>Approved leave requests are the record of leave. Every generation adds the month's
 * approved leave to the input, and leave given in the input is saved as approved leave, so
 * generating a month again never loses leave.
 */
@Service
@SuppressFBWarnings(value = "EI_EXPOSE_REP2",
		justification = "the stores and generator are Spring singletons injected on purpose; sharing them is the point")
public class RosterGenerationService {

	private final HospitalRosterGenerator generator;
	private final StaffStore staff;
	private final MonthInputStore inputs;
	private final RosterStore rosters;
	private final AuditStore audit;
	private final LeaveRequestStore leaveRequests;
	private final Clock clock;

	public RosterGenerationService(HospitalRosterGenerator generator, StaffStore staff, MonthInputStore inputs,
			RosterStore rosters, AuditStore audit, LeaveRequestStore leaveRequests, Clock clock) {
		this.generator = generator;
		this.staff = staff;
		this.inputs = inputs;
		this.rosters = rosters;
		this.audit = audit;
		this.leaveRequests = leaveRequests;
		this.clock = clock;
	}

	/**
	 * Generates and stores {@code input.month()}'s roster, replacing a draft one. An
	 * {@code Infeasible} result is stored too (the partial roster), so it can be finished by hand.
	 * Writes exactly one audit event. The roster is generated, and the input stored, with the
	 * month's approved leave added; each leave in {@code input} is saved as approved leave first.
	 *
	 * @throws IllegalStateException if the month already has a published roster
	 * @throws IllegalArgumentException if a leave in {@code input} is for someone not on the
	 *         doctor list, or has no day in the month
	 */
	@Transactional
	public GenerationResult generate(MonthInput input, Actor actor) {
		Objects.requireNonNull(input, "input");
		Objects.requireNonNull(actor, "actor");
		rosters.findByMonth(input.month())
				.filter(existing -> existing.status() == RosterStatus.PUBLISHED)
				.ifPresent(existing -> {
					throw new IllegalStateException(input.month() + " is published; it cannot be regenerated");
				});

		for (Staff doctor : input.doctors()) {
			staff.save(doctor);
		}
		MonthInput withLeave = MonthInputs.withCarriedIn(withApprovedLeave(input), rosters, inputs, generator.night());
		GenerationResult result = generator.generate(withLeave);
		inputs.save(MonthInputs.toStored(withLeave));
		String summary;
		Roster roster;
		switch (result) {
			case GenerationResult.Ok ok -> {
				roster = ok.roster();
				summary = "generated " + input.month() + ": every shift filled, SOFT penalty " + ok.softPenalty()
						+ brokenSuffix(roster, withLeave);
			}
			case GenerationResult.Infeasible infeasible -> {
				roster = infeasible.partial();
				summary = "generated " + input.month() + ": " + infeasible.unfilled().size() + " slots left unfilled"
						+ brokenSuffix(roster, withLeave);
			}
		}
		rosters.save(roster);
		List<Staff> owed = OwedOffs.after(roster, withLeave.doctors(), withLeave.leaves(), withLeave.owedOffs());
		if (!owed.isEmpty()) {
			summary += "; weekly offs owed to next month: "
					+ owed.stream().map(Staff::name).collect(Collectors.joining(", "));
		}
		audit.append(new AuditEvent(clock.instant(), actor.username(), input.month(), summary));
		return result;
	}

	/**
	 * Who does {@code month}'s first night so the rotation carries on from the month before:
	 * the doctor after that month's last night, in {@code doctors}' order.
	 *
	 * @return empty if the month before has no stored roster, or no night by anyone in {@code doctors}
	 */
	@Transactional(readOnly = true)
	public Optional<Staff> continueNights(YearMonth month, List<Staff> doctors) {
		Objects.requireNonNull(month, "month");
		return rosters.findByMonth(month.minusMonths(1))
				.flatMap(previous -> NightRotation.firstAfter(previous, generator.night(), doctors));
	}

	/**
	 * The HARD rules {@code month}'s stored roster breaks, checked against the input it was
	 * generated from. A weekly off that did not fit is not one of these: see {@link #owedOffsAfter}.
	 *
	 * @return empty if the month has no roster or no stored input
	 */
	@Transactional(readOnly = true)
	public List<Violation> brokenRules(YearMonth month) {
		Objects.requireNonNull(month, "month");
		Optional<Roster> roster = rosters.findByMonth(month);
		Optional<StoredMonthInput> stored = inputs.findByMonth(month);
		if (roster.isEmpty() || stored.isEmpty()) {
			return List.of();
		}
		return hardViolations(roster.get(),
				MonthInputs.withCarriedIn(MonthInputs.fromStored(stored.get()), rosters, inputs, generator.night()));
	}

	/**
	 * The weekly offs doctors are owed once {@code month} is over, which the next month's generation
	 * will give. A doctor owed two appears twice.
	 */
	@Transactional(readOnly = true)
	public List<Staff> owedOffsAfter(YearMonth month) {
		Objects.requireNonNull(month, "month");
		return MonthInputs.owedAfter(month, rosters, inputs);
	}

	/**
	 * The weekly offs {@code month} owes its doctors from earlier months, which it gives as extras
	 * where it has room. A doctor owed two appears twice.
	 *
	 * @return empty if the month has no stored input
	 */
	@Transactional(readOnly = true)
	public List<Staff> owedOffsInto(YearMonth month) {
		Objects.requireNonNull(month, "month");
		return inputs.findByMonth(month)
				.map(stored -> MonthInputs.withCarriedIn(MonthInputs.fromStored(stored), rosters, inputs, generator.night()).owedOffs())
				.orElse(List.of());
	}

	/**
	 * The nights in {@code month} that doctors on short leave still have to arrange: those where
	 * the doctor the rotation pencilled in is still on the night. A night already swapped or
	 * changed by hand is not listed.
	 *
	 * @return empty if the month has no roster or no stored input
	 */
	@Transactional(readOnly = true)
	public List<NightRotation.Cover> nightCovers(YearMonth month) {
		Objects.requireNonNull(month, "month");
		Optional<Roster> roster = rosters.findByMonth(month);
		Optional<StoredMonthInput> stored = inputs.findByMonth(month);
		if (roster.isEmpty() || stored.isEmpty()) {
			return List.of();
		}
		return generator.nightCovers(MonthInputs.withCarriedIn(MonthInputs.fromStored(stored.get()), rosters, inputs, generator.night()))
				.stream()
				.filter(cover -> roster.get().assignments().stream().anyMatch(a -> a.slot().shift().equals(generator.night())
						&& a.slot().date().equals(cover.date()) && a.staff().id().equals(cover.pencilledIn().id())))
				.toList();
	}

	/**
	 * Whether {@code month}'s stored roster differs from what its stored input generates, which
	 * means someone has changed it by hand. Generation is repeatable, so nothing extra is stored.
	 * If the month before has changed since, this also says true, which only makes callers keep
	 * the roster rather than replace it.
	 *
	 * @return false if the month has no roster or no stored input
	 */
	@Transactional(readOnly = true)
	public boolean hasHandEdits(YearMonth month) {
		Objects.requireNonNull(month, "month");
		Optional<Roster> stored = rosters.findByMonth(month);
		Optional<StoredMonthInput> input = inputs.findByMonth(month);
		if (stored.isEmpty() || input.isEmpty()) {
			return false;
		}
		Roster generated = switch (generator.generate(
				MonthInputs.withCarriedIn(MonthInputs.fromStored(input.get()), rosters, inputs, generator.night()))) {
			case GenerationResult.Ok ok -> ok.roster();
			case GenerationResult.Infeasible infeasible -> infeasible.partial();
		};
		return !new HashSet<>(generated.assignments()).equals(new HashSet<>(stored.get().assignments()))
				|| !new HashSet<>(generated.weeklyOffs()).equals(new HashSet<>(stored.get().weeklyOffs()));
	}

	private List<Violation> hardViolations(Roster roster, MonthInput input) {
		List<Violation> broken = new ArrayList<>();
		for (Constraint constraint : generator.constraints(input)) {
			if (constraint.severity() == Severity.HARD) {
				broken.addAll(constraint.check(roster));
			}
		}
		return broken;
	}

	private String brokenSuffix(Roster roster, MonthInput input) {
		int broken = hardViolations(roster, input).size();
		return broken == 0 ? "" : "; " + broken + " HARD rule" + (broken == 1 ? "" : "s") + " broken";
	}

	/** The holidays {@code month} was last generated with; none if it never was. */
	@Transactional(readOnly = true)
	public HospitalCalendar calendarFor(YearMonth month) {
		Objects.requireNonNull(month, "month");
		return inputs.findByMonth(month).map(StoredMonthInput::calendar).orElseGet(HospitalCalendar::noHolidays);
	}

	/**
	 * Generates {@code month} again from its stored input, with the month's approved leave.
	 *
	 * @throws IllegalArgumentException if the month has never been generated
	 * @throws IllegalStateException if the month's roster is published
	 */
	@Transactional
	public GenerationResult regenerate(YearMonth month, Actor actor) {
		Objects.requireNonNull(month, "month");
		return generate(MonthInputs.fromStored(inputs.findByMonth(month)
				.orElseThrow(() -> new IllegalArgumentException("no month input for " + month))), actor);
	}

	/**
	 * {@code input} with the month's approved leave added. Leave in {@code input} that is not
	 * approved yet is saved as approved. A leave is the same leave if the doctor and both days match.
	 */
	private MonthInput withApprovedLeave(MonthInput input) {
		Map<String, Staff> byId = input.doctors().stream()
				.collect(Collectors.toMap(Staff::id, Function.identity(), (first, second) -> first));
		List<LeaveRequest> approved = leaveRequests.findApproved(input.month());
		Set<String> seen = new HashSet<>();
		approved.forEach(request -> seen.add(key(request.toLeave())));
		List<Leave> leaves = new ArrayList<>();
		Set<String> added = new HashSet<>();
		for (Leave leave : input.leaves()) {
			if (!byId.containsKey(leave.staff().id())) {
				throw new IllegalArgumentException(leave.staff().id() + " has leave but is not on the doctor list");
			}
			if (leave.lastDay().isBefore(input.month().atDay(1)) || leave.firstDay().isAfter(input.month().atEndOfMonth())) {
				throw new IllegalArgumentException("leave for " + leave.staff().id() + " from " + leave.firstDay()
						+ " to " + leave.lastDay() + " has no day in " + input.month());
			}
			if (seen.add(key(leave))) {
				leaveRequests.save(new LeaveRequest(UUID.randomUUID().toString(), byId.get(leave.staff().id()),
						leave.firstDay(), leave.lastDay(), RequestState.APPROVED, clock.instant()));
			}
			if (added.add(key(leave))) {
				leaves.add(leave);
			}
		}
		for (LeaveRequest request : approved) {
			Staff doctor = byId.get(request.staff().id());
			if (doctor != null && added.add(key(request.toLeave()))) {
				leaves.add(new Leave(doctor, request.firstDay(), request.lastDay()));
			}
		}
		return new MonthInput(input.month(), input.doctors(), input.firstOnNights(), leaves, input.calendar(),
				input.weekdayPerShift(), input.weekendPerShift());
	}

	private static String key(Leave leave) {
		return leave.staff().id() + "|" + leave.firstDay() + "|" + leave.lastDay();
	}

}
