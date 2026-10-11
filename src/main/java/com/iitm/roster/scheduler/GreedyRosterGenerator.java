package com.iitm.roster.scheduler;

import com.iitm.roster.constraints.Constraint;
import com.iitm.roster.constraints.Severity;
import com.iitm.roster.constraints.Violation;
import com.iitm.roster.domain.Assignment;
import com.iitm.roster.domain.Roster;
import com.iitm.roster.domain.ShiftSlot;
import com.iitm.roster.domain.Staff;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Fills open slots one at a time, hardest first.
 *
 * <ol>
 *   <li>Order the open slots by how many people could take each one in the starting
 *       roster, fewest first, so the slots that are hard to staff are not left until
 *       everyone eligible has been used up.
 *   <li>For each slot, a staff member is a candidate if they have the right role, are not
 *       double-booked by it, and adding them creates no HARD violation that was not
 *       already there. Comparing with the violations already present, rather than
 *       requiring none, lets the generator work on a half-filled roster, where minimum
 *       staffing is still broken everywhere. (Comparing only the number of violations is
 *       not enough: filling a slot with someone on leave removes a staffing violation and
 *       adds a leave one, so the count stays the same.)
 *   <li>Pick the candidate giving the lowest SOFT penalty; on a tie, the one with the
 *       fewest shifts so far (spreads the work); then by staff id, so a run is repeatable.
 *   <li>A slot with no candidate is left unfilled and reported in {@code Infeasible}.
 * </ol>
 *
 * <p>No repair pass yet: once placed, an assignment is never moved.
 */
public final class GreedyRosterGenerator implements RosterGenerator {

	@Override
	public GenerationResult generate(GenerationRequest request) {
		List<Constraint> hard = bySeverity(request.constraints(), Severity.HARD);
		List<Constraint> soft = bySeverity(request.constraints(), Severity.SOFT);

		Roster roster = request.start();
		List<ShiftSlot> unfilled = new ArrayList<>();
		for (OpenSlot open : hardestFirst(request, hard)) {
			Optional<Roster> filled = bestFill(open, roster, request.staff(), hard, soft);
			if (filled.isPresent()) {
				roster = filled.get();
			} else {
				unfilled.add(open.slot());
			}
		}
		if (unfilled.isEmpty()) {
			return new GenerationResult.Ok(roster, totalPenalty(roster, soft));
		}
		return new GenerationResult.Infeasible(roster, unfilled);
	}

	private static List<OpenSlot> hardestFirst(GenerationRequest request, List<Constraint> hard) {
		List<OpenSlot> ordered = new ArrayList<>(request.openSlots());
		ordered.sort(Comparator
				.comparingInt((OpenSlot open) -> candidates(open, request.start(), request.staff(), hard).size())
				.thenComparing(open -> open.slot().start()));
		return ordered;
	}

	private static Optional<Roster> bestFill(OpenSlot open, Roster roster, List<Staff> staff,
			List<Constraint> hard, List<Constraint> soft) {
		Roster best = null;
		int bestPenalty = 0;
		int bestLoad = 0;
		for (Staff person : candidates(open, roster, staff, hard)) {
			Roster tried = roster.withAssignment(new Assignment(open.slot(), person));
			int penalty = totalPenalty(tried, soft);
			int load = roster.assignmentsFor(person).size();
			if (best == null || penalty < bestPenalty || penalty == bestPenalty && load < bestLoad) {
				best = tried;
				bestPenalty = penalty;
				bestLoad = load;
			}
		}
		return Optional.ofNullable(best);
	}

	/** Staff who could take {@code open} in {@code roster}, in staff-id order. */
	private static List<Staff> candidates(OpenSlot open, Roster roster, List<Staff> staff, List<Constraint> hard) {
		Set<Violation> hardBefore = violations(roster, hard);
		List<Staff> result = new ArrayList<>();
		for (Staff person : sortedById(staff)) {
			Assignment assignment = new Assignment(open.slot(), person);
			if (person.role() != open.role() || roster.wouldDoubleBook(assignment)) {
				continue;
			}
			if (hardBefore.containsAll(violations(roster.withAssignment(assignment), hard))) {
				result.add(person);
			}
		}
		return result;
	}

	private static List<Staff> sortedById(List<Staff> staff) {
		List<Staff> sorted = new ArrayList<>(staff);
		sorted.sort(Comparator.comparing(Staff::id));
		return sorted;
	}

	private static List<Constraint> bySeverity(List<Constraint> constraints, Severity severity) {
		return constraints.stream().filter(c -> c.severity() == severity).toList();
	}

	private static Set<Violation> violations(Roster roster, List<Constraint> constraints) {
		Set<Violation> all = new HashSet<>();
		for (Constraint constraint : constraints) {
			all.addAll(constraint.check(roster));
		}
		return all;
	}

	private static int totalPenalty(Roster roster, List<Constraint> constraints) {
		int total = 0;
		for (Constraint constraint : constraints) {
			total += constraint.penalty(roster);
		}
		return total;
	}

}
