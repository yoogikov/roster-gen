package com.iitm.roster.scheduler;

import com.iitm.roster.domain.Roster;
import com.iitm.roster.domain.ShiftSlot;
import java.util.List;
import java.util.Objects;

/** Either every open slot was filled, or some could not be and we say which. */
public sealed interface GenerationResult {

	/**
	 * Every open slot filled without adding a HARD violation; {@code softPenalty} is the
	 * total SOFT penalty of {@code roster}.
	 */
	record Ok(Roster roster, int softPenalty) implements GenerationResult {

		public Ok {
			Objects.requireNonNull(roster, "roster");
		}

	}

	/**
	 * Some slots could not be filled. {@code partial} holds what could be filled, so the
	 * CMO can finish the rest by hand.
	 */
	record Infeasible(Roster partial, List<ShiftSlot> unfilled) implements GenerationResult {

		public Infeasible {
			Objects.requireNonNull(partial, "partial");
			unfilled = List.copyOf(unfilled);
		}

	}

}
