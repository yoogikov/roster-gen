package com.iitm.roster.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.iitm.roster.repo.UserAccountStore;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/** Seeded demo logins and the copy-on-load behaviour of {@link AccountUserDetailsService}. */
@SpringBootTest
@ActiveProfiles("dev")
@TestPropertySource(properties = "roster.dev-users.password=" + DevUsersConfigTest.PASSWORD)
class DevUsersConfigTest {

	static final String PASSWORD = "test-only-password";

	@Autowired
	private UserDetailsService users;

	@Autowired
	private UserAccountStore accounts;

	@Autowired
	private PasswordEncoder encoder;

	@Test
	void eachSeededUserHasExactlyItsRole() {
		assertThat(rolesOf("admin")).containsExactly("ROLE_ADMIN");
		assertThat(rolesOf("reception")).containsExactly("ROLE_RECEPTION");
		assertThat(rolesOf("staff1")).containsExactly("ROLE_STAFF");
		assertThat(rolesOf("staff2")).containsExactly("ROLE_STAFF");
	}

	@Test
	void passwordIsStoredEncodedNotInPlainText() {
		String stored = users.loadUserByUsername("admin").getPassword();
		assertThat(stored).isNotEqualTo(PASSWORD).startsWith("{bcrypt}");
		assertThat(encoder.matches(PASSWORD, stored)).isTrue();
	}

	@Test
	void staffLoginsKeepTheirDoctorId() {
		assertThat(((RosterUser) users.loadUserByUsername("staff1")).staffId()).contains("D01");
		assertThat(((RosterUser) users.loadUserByUsername("staff2")).staffId()).contains("D06");
		assertThat(((RosterUser) users.loadUserByUsername("admin")).staffId()).isEmpty();
	}

	@Test
	void loadUserByUsernameReturnsAFreshCopy() {
		RosterUser first = (RosterUser) users.loadUserByUsername("admin");
		first.eraseCredentials();
		RosterUser second = (RosterUser) users.loadUserByUsername("admin");
		assertThat(second.getPassword()).isNotBlank();
		assertThat(encoder.matches(PASSWORD, second.getPassword())).isTrue();
		assertThat(accounts.findByUsername("admin")).get()
				.satisfies(account -> assertThat(account.passwordHash()).isNotBlank());
	}

	private List<String> rolesOf(String username) {
		return users.loadUserByUsername(username).getAuthorities().stream()
				.map(GrantedAuthority::getAuthority)
				.toList();
	}

}
