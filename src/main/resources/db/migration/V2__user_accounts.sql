-- Doctors can exist before a month is generated; inactive ones stay off new months.
alter table staff add column active boolean not null default true;

-- Logins in the database: one account per username, optional link to a doctor.
create table user_account (
	username varchar(255) not null,
	password_hash varchar(255) not null,
	role varchar(255) not null check ((role in ('ADMIN','RECEPTION','STAFF'))),
	staff_id varchar(255),
	enabled boolean not null,
	primary key (username)
);
alter table user_account add constraint FKuser_account_staff
	foreign key (staff_id) references staff;
