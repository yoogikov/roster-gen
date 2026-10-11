package com.iitm.roster.service;

import com.iitm.roster.constraints.Violation;
import com.iitm.roster.domain.Roster;
import java.util.List;
import java.util.Objects;

/** What {@link RosterService#applyEdit} did with an edit. */
public sealed interface EditResult {

	/** The edit was saved; {@code warnings} are SOFT violations it introduced. */
	record Applied(Roster roster, List<Violation> warnings) implements EditResult {

		public Applied {
			Objects.requireNonNull(roster, "roster");
			warnings = List.copyOf(warnings);
		}

	}

	/** The edit was refused and nothing was saved; {@code violations} are the HARD rules it would break. */
	record Rejected(List<Violation> violations) implements EditResult {

		public Rejected {
			violations = List.copyOf(violations);
			if (violations.isEmpty()) {
				throw new IllegalArgumentException("a rejection needs at least one violation");
			}
		}

	}

}
