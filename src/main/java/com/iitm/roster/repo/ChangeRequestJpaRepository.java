package com.iitm.roster.repo;

import com.iitm.roster.domain.RequestState;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface ChangeRequestJpaRepository extends JpaRepository<ChangeRequestEntity, String> {

	List<ChangeRequestEntity> findByStateInOrderByCreatedAtAsc(Collection<RequestState> states);

	List<ChangeRequestEntity> findByStaffIdOrFromStaffIdOrToStaffIdOrderByCreatedAtDesc(String staffId,
			String fromStaffId, String toStaffId);

}
