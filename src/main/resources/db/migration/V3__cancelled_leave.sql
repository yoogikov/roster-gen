-- Approved leave can now be cancelled.
alter table leave_request drop constraint leave_request_state_check;
alter table leave_request add constraint leave_request_state_check
	check (state in ('PENDING','ACCEPTED','APPROVED','REJECTED','LAPSED','CANCELLED'));
