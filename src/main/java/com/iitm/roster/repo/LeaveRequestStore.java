package com.iitm.roster.repo;

import com.iitm.roster.domain.LeaveRequest;
import com.iitm.roster.domain.RequestState;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Saves and loads leave requests. The doctor must already be saved. */
@Component
@Transactional
public class LeaveRequestStore {

	private final LeaveRequestJpaRepository requests;
	private final StaffStore staffStore;

	LeaveRequestStore(LeaveRequestJpaRepository requests, StaffStore staffStore) {
		this.requests = requests;
		this.staffStore = staffStore;
	}

	public void save(LeaveRequest request) {
		LeaveRequestEntity entity = requests.findById(request.id()).orElseGet(() -> new LeaveRequestEntity(request.id()));
		entity.update(request, staffStore.require(request.staff()));
		requests.save(entity);
	}

	@Transactional(readOnly = true)
	public Optional<LeaveRequest> findById(String id) {
		return requests.findById(id).map(LeaveRequestEntity::toDomain);
	}

	/** Still {@link RequestState#PENDING}, oldest first. */
	@Transactional(readOnly = true)
	public List<LeaveRequest> findOpen() {
		return requests.findByStateInOrderByCreatedAtAsc(List.of(RequestState.PENDING)).stream()
				.map(LeaveRequestEntity::toDomain)
				.toList();
	}

	/** Approved leave with at least one day in {@code month}, oldest request first. */
	@Transactional(readOnly = true)
	public List<LeaveRequest> findApproved(YearMonth month) {
		return requests.findByStateAndFirstDayLessThanEqualAndLastDayGreaterThanEqualOrderByCreatedAtAsc(
				RequestState.APPROVED, month.atEndOfMonth(), month.atDay(1)).stream()
				.map(LeaveRequestEntity::toDomain)
				.toList();
	}

	/** All approved leave, oldest request first. */
	@Transactional(readOnly = true)
	public List<LeaveRequest> findAllApproved() {
		return requests.findByStateInOrderByCreatedAtAsc(List.of(RequestState.APPROVED)).stream()
				.map(LeaveRequestEntity::toDomain)
				.toList();
	}

	/** Every leave request for this doctor, newest first. */
	@Transactional(readOnly = true)
	public List<LeaveRequest> findForStaff(String staffId) {
		return requests.findByStaff_IdOrderByCreatedAtDesc(staffId).stream().map(LeaveRequestEntity::toDomain).toList();
	}

}
