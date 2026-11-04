package com.iitm.roster.requests;

import com.iitm.roster.domain.LeaveRequest;
import com.iitm.roster.security.Decision;

/** What proposing, approving or rejecting a leave request did. */
public sealed interface LeaveResult {

	record Pending(LeaveRequest request) implements LeaveResult {
	}

	record Approved(LeaveRequest request) implements LeaveResult {
	}

	record Rejected(LeaveRequest request) implements LeaveResult {
	}

	record NotAllowed(Decision decision) implements LeaveResult {
	}

	record Refused(String reason) implements LeaveResult {
	}

}
