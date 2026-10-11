package com.iitm.roster.constraints;

import com.iitm.roster.domain.Roster;
import java.util.List;

/** One rule a roster must (HARD) or should (SOFT) obey. */
public interface Constraint {

	/** Stable identifier, shown with each violation. */
	String id();

	Severity severity();

	/** Everything wrong with {@code roster} under this rule; empty if it is satisfied. */
	List<Violation> check(Roster roster);

	/** How bad the SOFT violations are, for comparing rosters; always 0 for a HARD constraint. */
	int penalty(Roster roster);

}
