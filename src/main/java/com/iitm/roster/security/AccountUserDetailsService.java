package com.iitm.roster.security;

import com.iitm.roster.repo.UserAccount;
import com.iitm.roster.repo.UserAccountStore;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads logins from {@link UserAccountStore}. A fresh {@link RosterUser} is returned each time:
 * the security filter erases the password on the object it authenticated, and that must not wipe
 * the stored hash.
 */
@Service
public class AccountUserDetailsService implements UserDetailsService {

	private final UserAccountStore accounts;

	public AccountUserDetailsService(UserAccountStore accounts) {
		this.accounts = accounts;
	}

	@Override
	@Transactional(readOnly = true)
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		UserAccount account = accounts.findByUsername(username)
				.orElseThrow(() -> new UsernameNotFoundException(username));
		return new RosterUser(account.username(), account.passwordHash(), account.enabled(), account.role(),
				account.staffId());
	}

}
