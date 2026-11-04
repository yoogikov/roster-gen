package com.iitm.roster.requests;

import com.iitm.roster.domain.ChangeRequest;
import com.iitm.roster.domain.LeaveRequest;
import com.iitm.roster.domain.SwapRequest;
import com.iitm.roster.repo.SwapRequestStore;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The open requests an admin's inbox shows. */
@Service
public class RequestInbox {

	private final SwapRequestStore swaps;
	private final ChangeLifecycle changes;
	private final LeaveLifecycle leaves;

	public RequestInbox(SwapRequestStore swaps, ChangeLifecycle changes, LeaveLifecycle leaves) {
		this.swaps = swaps;
		this.changes = changes;
		this.leaves = leaves;
	}

	@Transactional(readOnly = true)
	public Open open() {
		return new Open(swaps.findOpen(), changes.open(), leaves.open());
	}

	public record Open(List<SwapRequest> swaps, List<ChangeRequest> changes, List<LeaveRequest> leaves) {

		public Open {
			swaps = List.copyOf(swaps);
			changes = List.copyOf(changes);
			leaves = List.copyOf(leaves);
		}

	}

}
