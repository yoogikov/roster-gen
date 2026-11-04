package com.iitm.roster.requests;

import com.iitm.roster.domain.ChangeRequest;
import com.iitm.roster.security.Decision;
import com.iitm.roster.service.EditResult;

/** What filing, approving or rejecting a stored edit did. */
public sealed interface ChangeResult {

	record Pending(ChangeRequest request) implements ChangeResult {
	}

	record Approved(ChangeRequest request) implements ChangeResult {
	}

	record Rejected(ChangeRequest request) implements ChangeResult {
	}

	record NotAllowed(Decision decision) implements ChangeResult {
	}

	/** The admin approved it, but the roster rules refused the edit. The request stays pending. */
	record EditRejected(ChangeRequest request, EditResult.Rejected edit) implements ChangeResult {
	}

}
