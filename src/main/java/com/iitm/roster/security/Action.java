package com.iitm.roster.security;

import com.iitm.roster.domain.Assignment;
import com.iitm.roster.domain.ShiftSlot;
import com.iitm.roster.domain.SwapRequest;
import java.util.Objects;
import java.util.Set;

/** Something a signed-in user wants to do to a month's roster. The roster itself is passed separately. */
public sealed interface Action {

	record ViewRoster() implements Action {
	}

	record GenerateRoster() implements Action {
	}

	record PublishRoster() implements Action {
	}

	/**
	 * Change who works {@code slot}.
	 *
	 * @param staffIds everyone whose shifts the edit changes (e.g. both people in a move)
	 */
	record EditShift(ShiftSlot slot, Set<String> staffIds) implements Action {

		public EditShift {
			Objects.requireNonNull(slot, "slot");
			staffIds = Set.copyOf(staffIds);
			if (staffIds.isEmpty()) {
				throw new IllegalArgumentException("an edit changes at least one person's shifts");
			}
		}

	}

	/** Ask to exchange {@code offered} (the proposer's) for {@code wanted} (someone else's). */
	record ProposeSwap(Assignment offered, Assignment wanted) implements Action {

		public ProposeSwap {
			Objects.requireNonNull(offered, "offered");
			Objects.requireNonNull(wanted, "wanted");
		}

	}

	/** The other staff member agrees to a proposed swap. */
	record AcceptSwap(SwapRequest request) implements Action {

		public AcceptSwap {
			Objects.requireNonNull(request, "request");
		}

	}

	/** Approve or reject a swap. */
	record DecideSwap(SwapRequest request) implements Action {

		public DecideSwap {
			Objects.requireNonNull(request, "request");
		}

	}

}
