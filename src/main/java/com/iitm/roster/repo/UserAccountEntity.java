package com.iitm.roster.repo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_account")
class UserAccountEntity {

	@Id
	private String username;

	@Column(name = "password_hash", nullable = false)
	private String passwordHash;

	@Column(nullable = false)
	private String role;

	@Column(name = "staff_id")
	private String staffId;

	@Column(nullable = false)
	private boolean enabled;

	protected UserAccountEntity() {
		// Required by JPA, which instantiates the class before setting fields; not for application code.
	}

	UserAccountEntity(String username) {
		this.username = username;
	}

	void update(UserAccount account) {
		this.passwordHash = account.passwordHash();
		this.role = account.role();
		this.staffId = blankToNull(account.staffId());
		this.enabled = account.enabled();
	}

	UserAccount toDomain() {
		return new UserAccount(username, passwordHash, role, staffId, enabled);
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value;
	}

}
