package com.iitm.roster.repo;

import com.iitm.roster.domain.RequestState;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface LeaveRequestJpaRepository extends JpaRepository<LeaveRequestEntity, String> {

	List<LeaveRequestEntity> findByStateInOrderByCreatedAtAsc(Collection<RequestState> states);

}
