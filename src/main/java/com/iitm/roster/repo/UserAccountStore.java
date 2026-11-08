package com.iitm.roster.repo;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Saves and loads logins. Passwords must already be hashed when saved. */
@Component
@Transactional
public class UserAccountStore {

	private final UserAccountJpaRepository accounts;

	UserAccountStore(UserAccountJpaRepository accounts) {
		this.accounts = accounts;
	}

	public void save(UserAccount account) {
		accounts.findById(account.username()).ifPresentOrElse(
				existing -> existing.update(account),
				() -> {
					UserAccountEntity entity = new UserAccountEntity(account.username());
					entity.update(account);
					accounts.save(entity);
				});
	}

	@Transactional(readOnly = true)
	public Optional<UserAccount> findByUsername(String username) {
		return accounts.findByUsername(username).map(UserAccountEntity::toDomain);
	}

	@Transactional(readOnly = true)
	public Optional<UserAccount> findByStaffId(String staffId) {
		return accounts.findByStaffId(staffId).map(UserAccountEntity::toDomain);
	}

	@Transactional(readOnly = true)
	public boolean hasAdmin() {
		return accounts.existsByRole("ADMIN");
	}

	@Transactional(readOnly = true)
	public List<UserAccount> findAll() {
		return accounts.findAllByOrderByUsernameAsc().stream().map(UserAccountEntity::toDomain).toList();
	}

}
