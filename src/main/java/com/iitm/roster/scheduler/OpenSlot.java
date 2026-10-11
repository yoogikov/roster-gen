package com.iitm.roster.scheduler;

import com.iitm.roster.domain.Post;
import com.iitm.roster.domain.ShiftSlot;
import com.iitm.roster.domain.StaffRole;
import java.util.Objects;

/** One person of {@code role} still needed on {@code slot}, in {@code post}. */
public record OpenSlot(ShiftSlot slot, StaffRole role, Post post) {

	public OpenSlot {
		Objects.requireNonNull(slot, "slot");
		Objects.requireNonNull(role, "role");
		Objects.requireNonNull(post, "post");
	}

	/** A general-duty place. */
	public OpenSlot(ShiftSlot slot, StaffRole role) {
		this(slot, role, Post.GENERAL);
	}

}
