package com.iitm.roster.service;

import com.iitm.roster.domain.Actor;
import com.iitm.roster.domain.AuditEvent;
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
import com.iitm.roster.scheduler.GenerationResult;
import com.iitm.roster.scheduler.HospitalRosterGenerator;
import com.iitm.roster.scheduler.MonthInput;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Clock;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
		MonthInput withLeave = withApprovedLeave(input);
		GenerationResult result = generator.generate(withLeave);
		inputs.save(MonthInputs.toStored(withLeave));
		String summary;
		Roster roster;
		switch (result) {
			case GenerationResult.Ok ok -> {
				roster = ok.roster();
				summary = "generated " + input.month() + ": every shift filled, SOFT penalty " + ok.softPenalty();
			}
			case GenerationResult.Infeasible infeasible -> {
				roster = infeasible.partial();
				summary = "generated " + input.month() + ": " + infeasible.unfilled().size() + " slots left unfilled";
			}
		}
		rosters.save(roster);
		audit.append(new AuditEvent(clock.instant(), actor.username(), input.month(), summary));
		return result;
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
