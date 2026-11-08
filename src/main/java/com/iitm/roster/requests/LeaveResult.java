package com.iitm.roster.requests;

import com.iitm.roster.domain.Assignment;
import com.iitm.roster.domain.LeaveRequest;
import com.iitm.roster.security.Decision;
import java.util.List;

/** What proposing, approving or rejecting a leave request did. */
public sealed interface LeaveResult {

	record Pending(LeaveRequest request) implements LeaveResult {
	}

	/**
	 * The leave is approved. If the month's roster was a draft with no hand edits it was
	 * {@code regenerated} with the leave; if it was published or hand-edited, {@code freed} lists
	 * the shifts the doctor was taken off, now for the admin to fill.
	 */
	record Approved(LeaveRequest request, List<Assignment> freed, boolean regenerated) implements LeaveResult {

		public Approved {
			freed = List.copyOf(freed);
		}

	}

	record Rejected(LeaveRequest request) implements LeaveResult {
	}

	/** Approved leave taken back. If its month's draft had no hand edits it was {@code regenerated}. */
	record Cancelled(LeaveRequest request, boolean regenerated) implements LeaveResult {
	}

	record NotAllowed(Decision decision) implements LeaveResult {
	}

	record Refused(String reason) implements LeaveResult {
	}

}
