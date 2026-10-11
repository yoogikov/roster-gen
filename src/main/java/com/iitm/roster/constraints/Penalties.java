package com.iitm.roster.constraints;

import java.util.List;

/** Shared penalty rule for the constraints in this package. */
final class Penalties {

	private Penalties() {
	}

	/** One point per violation for a SOFT constraint; always 0 for a HARD one. */
	static int perViolation(Severity severity, List<Violation> violations) {
		return severity == Severity.SOFT ? violations.size() : 0;
	}

}
