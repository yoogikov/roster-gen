package com.iitm.roster.repo;

import com.iitm.roster.domain.RequestState;
import com.iitm.roster.domain.SwapRequest;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Saves and loads {@link SwapRequest}s. Saving an existing id overwrites it (e.g. a new state). */
@Component
@Transactional
public class SwapRequestStore {

	private final SwapRequestJpaRepository requests;
	private final StaffStore staffStore;

	SwapRequestStore(SwapRequestJpaRepository requests, StaffStore staffStore) {
		this.requests = requests;
		this.staffStore = staffStore;
	}

	public void save(SwapRequest request) {
		AssignmentEmbeddable offered = new AssignmentEmbeddable(request.offered(), staffStore.require(request.offered().staff()));
		AssignmentEmbeddable wanted = new AssignmentEmbeddable(request.wanted(), staffStore.require(request.wanted().staff()));
		SwapRequestEntity entity = requests.findById(request.id())
				.orElseGet(() -> new SwapRequestEntity(request.id()));
		entity.update(request, offered, wanted);
		requests.save(entity);
	}

	@Transactional(readOnly = true)
	public Optional<SwapRequest> findById(String id) {
		return requests.findById(id).map(SwapRequestEntity::toDomain);
	}

	/** Requests still PENDING or ACCEPTED, oldest first. */
	@Transactional(readOnly = true)
	public List<SwapRequest> findOpen() {
		List<RequestState> open = Arrays.stream(RequestState.values()).filter(RequestState::isOpen).toList();
		return requests.findByStateInOrderByCreatedAtAsc(open).stream().map(SwapRequestEntity::toDomain).toList();
	}

}
