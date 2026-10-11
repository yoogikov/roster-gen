package com.iitm.roster.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

class DevUsersConfigTest {

	private final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

	private final InMemoryUserDetailsManager users = new DevUsersConfig().devUsers("pw", encoder);

	@Test
	void eachDevUserHasExactlyItsRole() {
		assertThat(rolesOf("admin")).containsExactly("ROLE_ADMIN");
		assertThat(rolesOf("reception")).containsExactly("ROLE_RECEPTION");
		assertThat(rolesOf("staff1")).containsExactly("ROLE_STAFF");
		assertThat(rolesOf("staff2")).containsExactly("ROLE_STAFF");
	}

	@Test
	void passwordIsStoredEncodedNotInPlainText() {
		String stored = users.loadUserByUsername("admin").getPassword();
		assertThat(stored).isNotEqualTo("pw").startsWith("{bcrypt}");
		assertThat(encoder.matches("pw", stored)).isTrue();
	}

	@Test
	void configuredPasswordIsUsedAsGiven() {
		assertThat(DevUsersConfig.resolvePassword("secret")).isEqualTo("secret");
	}

	@Test
	void blankPasswordFallsBackToRandomOne() {
		String first = DevUsersConfig.resolvePassword("  ");
		String second = DevUsersConfig.resolvePassword(null);
		assertThat(first).isNotBlank().isNotEqualTo(second);
	}

	private List<String> rolesOf(String username) {
		return users.loadUserByUsername(username).getAuthorities().stream()
				.map(GrantedAuthority::getAuthority)
				.toList();
	}

}
