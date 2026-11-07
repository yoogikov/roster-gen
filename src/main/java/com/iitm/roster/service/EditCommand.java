package com.iitm.roster.service;

import com.iitm.roster.domain.Post;
import com.iitm.roster.domain.ShiftSlot;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;

/** One change to a roster, applied by {@link RosterService#applyEdit}. Staff are given by id. */
public sealed interface EditCommand {

	/** The month whose roster this edit changes. */
	YearMonth month();

	/** Put {@code staffId} on {@code slot} in {@code post}. */
	record Assign(ShiftSlot slot, String staffId, Post post) implements EditCommand {

		public Assign {
			Objects.requireNonNull(slot, "slot");
			requireId(staffId, "staffId");
			Objects.requireNonNull(post, "post");
		}

		/** General duty. */
		public Assign(ShiftSlot slot, String staffId) {
			this(slot, staffId, Post.GENERAL);
		}

		@Override
		public YearMonth month() {
			return YearMonth.from(slot.date());
		}

	}

	/** Take {@code staffId} off {@code slot}, whatever their post. */
	record Unassign(ShiftSlot slot, String staffId) implements EditCommand {

		public Unassign {
			Objects.requireNonNull(slot, "slot");
			requireId(staffId, "staffId");
		}

		@Override
		public YearMonth month() {
			return YearMonth.from(slot.date());
		}

	}

	/** Hand {@code slot} from {@code fromStaffId} to {@code toStaffId}, keeping the post. */
	record Move(ShiftSlot slot, String fromStaffId, String toStaffId) implements EditCommand {

		public Move {
			Objects.requireNonNull(slot, "slot");
			requireId(fromStaffId, "fromStaffId");
			requireId(toStaffId, "toStaffId");
			if (fromStaffId.equals(toStaffId)) {
				throw new IllegalArgumentException("cannot move a slot to the same person: " + fromStaffId);
			}
		}

		@Override
		public YearMonth month() {
			return YearMonth.from(slot.date());
		}

	}

	/** Move one doctor's weekly off from {@code fromDate} to {@code toDate} in the same month. */
	record MoveWeeklyOff(LocalDate fromDate, String staffId, LocalDate toDate) implements EditCommand {

		public MoveWeeklyOff {
			Objects.requireNonNull(fromDate, "fromDate");
			requireId(staffId, "staffId");
			Objects.requireNonNull(toDate, "toDate");
			if (!YearMonth.from(fromDate).equals(YearMonth.from(toDate))) {
				throw new IllegalArgumentException("weekly off must stay inside one month");
			}
			if (fromDate.equals(toDate)) {
				throw new IllegalArgumentException("weekly off is already on " + toDate);
			}
		}

		@Override
		public YearMonth month() {
			return YearMonth.from(fromDate);
		}

	}

	private static void requireId(String id, String field) {
		if (id == null || id.isBlank()) {
			throw new IllegalArgumentException(field + " must not be blank");
		}
	}

}
