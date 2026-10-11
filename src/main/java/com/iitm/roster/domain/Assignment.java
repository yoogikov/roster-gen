package com.iitm.roster.domain;

import java.util.Objects;

/** One staff member rostered on one slot, in one {@link Post}. */
public record Assignment(ShiftSlot slot, Staff staff, Post post) {

	public Assignment {
		Objects.requireNonNull(slot, "slot");
		Objects.requireNonNull(staff, "staff");
		Objects.requireNonNull(post, "post");
	}

	/** A general-duty assignment. */
	public Assignment(ShiftSlot slot, Staff staff) {
		this(slot, staff, Post.GENERAL);
	}

}
