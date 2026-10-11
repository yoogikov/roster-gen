package com.iitm.roster.security;

import com.iitm.roster.domain.Role;
import java.util.Objects;

/** What {@link AuthorizationPolicy} says about an action. */
public sealed interface Decision {

	/** Go ahead. */
	record Allow() implements Decision {
	}

	/** Refused; {@code reason} is shown to the user. */
	record Deny(String reason) implements Decision {

		public Deny {
			if (reason == null || reason.isBlank()) {
				throw new IllegalArgumentException("reason must not be blank");
			}
		}

	}

	/** Not done now: the action becomes a request that someone with {@code approver} must approve. */
	record RequiresApproval(Role approver) implements Decision {

		public RequiresApproval {
			Objects.requireNonNull(approver, "approver");
		}

	}

}
