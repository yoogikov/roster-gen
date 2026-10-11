package com.iitm.roster.security;

import com.iitm.roster.domain.Actor;
import com.iitm.roster.domain.Roster;

/** Decides whether {@code actor} may do {@code action} to the {@code target} roster. */
public interface AuthorizationPolicy {

	/** Allow, Deny with a reason, or RequiresApproval naming who must approve it. */
	Decision may(Actor actor, Action action, Roster target);

}
