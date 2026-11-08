package com.iitm.roster.scheduler;

import com.iitm.roster.constraints.Constraint;
import com.iitm.roster.constraints.Violation;
import com.iitm.roster.domain.Assignment;
import com.iitm.roster.domain.Roster;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Lowers a roster's SOFT penalty after it has been filled, by exchanging the duties of two doctors
 * working the same day. Each doctor keeps the same number of shifts on the same days. An exchange
 * is kept only if it lowers the penalty and adds no HARD violation, so the penalty only ever falls
 * and the pass always ends.
 */
final class SoftRepair {

	private SoftRepair() {
	}

	static Roster improve(Roster start, List<Constraint> hard, List<Constraint> soft) {
		Roster roster = start;
		boolean improved = true;
		while (improved) {
			improved = false;
			for (LocalDate day : roster.days()) {
				Optional<Roster> better = betterExchange(roster, day, hard, soft);
				while (better.isPresent()) {
					roster = better.get();
					improved = true;
					better = betterExchange(roster, day, hard, soft);
				}
			}
		}
		return roster;
	}

	/** The first exchange on {@code day}, in a repeatable order, that makes the roster better. */
	private static Optional<Roster> betterExchange(Roster roster, LocalDate day, List<Constraint> hard, List<Constraint> soft) {
		int penalty = penalty(roster, soft);
		if (penalty == 0) {
			return Optional.empty();
		}
		Set<Violation> hardBefore = violations(roster, hard);
		List<Assignment> today = roster.assignments().stream().filter(a -> a.slot().date().equals(day)).toList();
		for (int i = 0; i < today.size(); i++) {
			for (int j = i + 1; j < today.size(); j++) {
				Optional<Roster> exchanged = exchange(roster, today.get(i), today.get(j));
				if (exchanged.isPresent() && penalty(exchanged.get(), soft) < penalty
						&& hardBefore.containsAll(violations(exchanged.get(), hard))) {
					return exchanged;
				}
			}
		}
		return Optional.empty();
	}

	/** {@code roster} with the doctors of {@code a} and {@code b} exchanged; empty if pointless or a double-booking. */
	private static Optional<Roster> exchange(Roster roster, Assignment a, Assignment b) {
		if (a.staff().equals(b.staff()) || a.slot().equals(b.slot()) && a.post() == b.post()) {
			return Optional.empty();
		}
		Assignment newA = new Assignment(a.slot(), b.staff(), a.post());
		Assignment newB = new Assignment(b.slot(), a.staff(), b.post());
		Roster without = roster.withoutAssignment(a).withoutAssignment(b);
		if (without.wouldDoubleBook(newA)) {
			return Optional.empty();
		}
		Roster withA = without.withAssignment(newA);
		return withA.wouldDoubleBook(newB) ? Optional.empty() : Optional.of(withA.withAssignment(newB));
	}

	private static int penalty(Roster roster, List<Constraint> soft) {
		return soft.stream().mapToInt(c -> c.penalty(roster)).sum();
	}

	private static Set<Violation> violations(Roster roster, List<Constraint> hard) {
		Set<Violation> all = new HashSet<>();
		hard.forEach(c -> all.addAll(c.check(roster)));
		return all;
	}

}
