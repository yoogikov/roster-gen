package com.iitm.roster.requests;

import com.iitm.roster.domain.SwapRequest;
import com.iitm.roster.security.Decision;
import com.iitm.roster.service.EditResult;
import java.util.Objects;

/** What {@link SwapLifecycle} did with a swap. */
public sealed interface SwapResult {

	/** A new request, still {@code PENDING}. */
	record Pending(SwapRequest request) implements SwapResult {

		public Pending {
			Objects.requireNonNull(request, "request");
		}

	}

	/** The other person agreed. */
	record Accepted(SwapRequest request) implements SwapResult {

		public Accepted {
			Objects.requireNonNull(request, "request");
		}

	}

	/** Both assignments were moved and the request is {@code APPROVED}. */
	record Approved(SwapRequest request) implements SwapResult {

		public Approved {
			Objects.requireNonNull(request, "request");
		}

	}

	/** The request is {@code REJECTED}. The roster was not changed. */
	record Rejected(SwapRequest request) implements SwapResult {

		public Rejected {
			Objects.requireNonNull(request, "request");
		}

	}

	/** {@link com.iitm.roster.security.AuthorizationPolicy} did not allow it. Nothing was stored. */
	record NotAllowed(Decision decision) implements SwapResult {

		public NotAllowed {
			Objects.requireNonNull(decision, "decision");
			if (decision instanceof Decision.Allow) {
				throw new IllegalArgumentException("Allow is not a refusal");
			}
		}

	}

	/** The roster edit was rejected. The request stays as it was and nothing is saved. */
	record EditRejected(SwapRequest request, EditResult.Rejected edit) implements SwapResult {

		public EditRejected {
			Objects.requireNonNull(request, "request");
			Objects.requireNonNull(edit, "edit");
		}

	}

}
