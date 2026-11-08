package com.iitm.roster.domain;

/**
 * Where a swap request is. PENDING and ACCEPTED are open; APPROVED, REJECTED and LAPSED
 * are final. An open request still open when its shift starts becomes LAPSED. CANCELLED is for
 * approved leave taken back afterwards.
 */
public enum RequestState {
	PENDING,
	ACCEPTED,
	APPROVED,
	REJECTED,
	LAPSED,
	CANCELLED;

	public boolean isOpen() {
		return this == PENDING || this == ACCEPTED;
	}
}
