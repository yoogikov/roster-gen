package com.iitm.roster.service;

import com.iitm.roster.domain.Actor;
import com.iitm.roster.domain.AuditEvent;
import com.iitm.roster.domain.Roster;
import com.iitm.roster.domain.RosterStatus;
import com.iitm.roster.domain.Staff;
import com.iitm.roster.repo.AuditStore;
import com.iitm.roster.repo.MonthInputStore;
import com.iitm.roster.repo.RosterStore;
import com.iitm.roster.repo.StaffStore;
import com.iitm.roster.scheduler.GenerationResult;
import com.iitm.roster.scheduler.HospitalRosterGenerator;
import com.iitm.roster.scheduler.MonthInput;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Clock;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generates a month's roster from a doctor list and the hospital rules, and stores it
 * together with the input, so later edits are checked against the same rules.
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
	private final Clock clock;

	public RosterGenerationService(HospitalRosterGenerator generator, StaffStore staff, MonthInputStore inputs,
			RosterStore rosters, AuditStore audit, Clock clock) {
		this.generator = generator;
		this.staff = staff;
		this.inputs = inputs;
		this.rosters = rosters;
		this.audit = audit;
		this.clock = clock;
	}

	/**
	 * Generates and stores {@code input.month()}'s roster, replacing a draft one. An
	 * {@code Infeasible} result is stored too (the partial roster), so it can be finished by hand.
	 * Writes exactly one audit event.
	 *
	 * @throws IllegalStateException if the month already has a published roster
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

		GenerationResult result = generator.generate(input);
		for (Staff doctor : input.doctors()) {
			staff.save(doctor);
		}
		inputs.save(MonthInputs.toStored(input));
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

}
