package com.iitm.roster.repo;

import com.iitm.roster.domain.RequestState;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface LeaveRequestJpaRepository extends JpaRepository<LeaveRequestEntity, String> {

	List<LeaveRequestEntity> findByStateInOrderByCreatedAtAsc(Collection<RequestState> states);

	/** Requests in {@code state} that start on or before {@code to} and end on or after {@code from}. */
	List<LeaveRequestEntity> findByStateAndFirstDayLessThanEqualAndLastDayGreaterThanEqualOrderByCreatedAtAsc(
			RequestState state, LocalDate to, LocalDate from);

	@SuppressWarnings("PMD.MethodNamingConventions") // Spring Data needs the underscore for the association path
	List<LeaveRequestEntity> findByStaff_IdOrderByCreatedAtDesc(String staffId);

}
