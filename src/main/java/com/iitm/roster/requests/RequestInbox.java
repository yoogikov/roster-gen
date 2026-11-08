package com.iitm.roster.requests;

import com.iitm.roster.domain.Actor;
import com.iitm.roster.domain.ChangeRequest;
import com.iitm.roster.domain.LeaveRequest;
import com.iitm.roster.domain.SwapRequest;
import com.iitm.roster.repo.ChangeRequestStore;
import com.iitm.roster.repo.LeaveRequestStore;
import com.iitm.roster.repo.SwapRequestStore;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Open requests for the admin inbox, and a staff member's own request history. */
@Service
public class RequestInbox {

	private final SwapRequestStore swaps;
	private final ChangeRequestStore changes;
	private final LeaveRequestStore leaves;
	private final ChangeLifecycle changeLifecycle;
	private final LeaveLifecycle leaveLifecycle;

	public RequestInbox(SwapRequestStore swaps, ChangeRequestStore changes, LeaveRequestStore leaves,
			ChangeLifecycle changeLifecycle, LeaveLifecycle leaveLifecycle) {
		this.swaps = swaps;
		this.changes = changes;
		this.leaves = leaves;
		this.changeLifecycle = changeLifecycle;
		this.leaveLifecycle = leaveLifecycle;
	}

	@Transactional(readOnly = true)
	public Open open() {
		return new Open(swaps.findOpen(), changeLifecycle.open(), leaveLifecycle.open());
	}

	/** Every request that involves this actor's doctor id, newest first. Empty when not STAFF. */
	@Transactional(readOnly = true)
	public Mine mine(Actor actor) {
		String staffId = actor.staffId().orElse(null);
		if (staffId == null) {
			return Mine.empty();
		}
		return new Mine(swaps.findForStaff(staffId), changes.findForStaff(staffId), leaves.findForStaff(staffId));
	}

	public record Open(List<SwapRequest> swaps, List<ChangeRequest> changes, List<LeaveRequest> leaves) {

		public Open {
			swaps = List.copyOf(swaps);
			changes = List.copyOf(changes);
			leaves = List.copyOf(leaves);
		}

	}

	public record Mine(List<SwapRequest> swaps, List<ChangeRequest> changes, List<LeaveRequest> leaves) {

		public Mine {
			swaps = List.copyOf(swaps);
			changes = List.copyOf(changes);
			leaves = List.copyOf(leaves);
		}

		static Mine empty() {
			return new Mine(List.of(), List.of(), List.of());
		}

	}

}
