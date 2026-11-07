package com.iitm.roster.security;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * The same four synthetic logins as the dev profile, for a local Postgres deploy.
 * The password comes from {@code ROSTER_ADMIN_PASSWORD}; there is no default.
 * They are not a hospital user store (gap 10).
 */
@Configuration
@Profile("prod")
public class ProdUsersConfig {

	@Bean
	UserDetailsService prodUsers(
			@Value("${roster.admin-password}") String password,
			PasswordEncoder passwordEncoder) {
		if (password == null || password.isBlank()) {
			throw new IllegalStateException("ROSTER_ADMIN_PASSWORD must be set in prod");
		}
		String encoded = passwordEncoder.encode(password);
		Map<String, RosterUser> users = Map.of(
				"admin", new RosterUser("admin", encoded, "ADMIN", null),
				"reception", new RosterUser("reception", encoded, "RECEPTION", null),
				"staff1", new RosterUser("staff1", encoded, "STAFF", "D01"),
				"staff2", new RosterUser("staff2", encoded, "STAFF", "D06"));
		return username -> {
			RosterUser found = users.get(username);
			if (found == null) {
				throw new UsernameNotFoundException(username);
			}
			return new RosterUser(found.getUsername(), found.getPassword(), found.role(), found.staffId().orElse(null));
		};
	}

}
