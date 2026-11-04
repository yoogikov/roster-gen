package com.iitm.roster.web;

import com.iitm.roster.domain.Post;
import java.time.LocalDate;

/** JSON for {@code POST /api/swaps}. Shift codes are {@code I}, {@code II} and {@code N}. */
public record ProposeBody(LocalDate offeredDate, String offeredShift, String offeredStaffId, Post offeredPost,
		LocalDate wantedDate, String wantedShift, String wantedStaffId, Post wantedPost) {
}
