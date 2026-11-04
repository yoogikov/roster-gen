package com.iitm.roster.repo;

import com.iitm.roster.domain.ChangeRequest;
import com.iitm.roster.domain.RequestState;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Saves and loads edits that are waiting for approval. */
@Component
@Transactional
public class ChangeRequestStore {

	private final ChangeRequestJpaRepository requests;

	ChangeRequestStore(ChangeRequestJpaRepository requests) {
		this.requests = requests;
	}

	public void save(ChangeRequest request) {
		ChangeRequestEntity entity = requests.findById(request.id()).orElseGet(() -> new ChangeRequestEntity(request.id()));
		entity.update(request);
		requests.save(entity);
	}

	@Transactional(readOnly = true)
	public Optional<ChangeRequest> findById(String id) {
		return requests.findById(id).map(ChangeRequestEntity::toDomain);
	}

	/** Still {@link RequestState#PENDING}, oldest first. */
	@Transactional(readOnly = true)
	public List<ChangeRequest> findOpen() {
		return requests.findByStateInOrderByCreatedAtAsc(List.of(RequestState.PENDING)).stream()
				.map(ChangeRequestEntity::toDomain)
				.toList();
	}

}
