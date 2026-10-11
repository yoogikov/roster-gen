package com.iitm.roster.repo;

import com.iitm.roster.domain.RequestState;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface SwapRequestJpaRepository extends JpaRepository<SwapRequestEntity, String> {

	List<SwapRequestEntity> findByStateInOrderByCreatedAtAsc(Collection<RequestState> states);

}
