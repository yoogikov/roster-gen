package com.iitm.roster.security;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

/**
 * Synthetic in-memory users, one or more per role, for the dev profile only.
 * They are not real hospital staff. All share one password, taken from
 * {@code ROSTER_DEV_PASSWORD}; if that is unset, a random one is generated and logged.
 */
@Configuration
@Profile("dev")
public class DevUsersConfig {

	private static final Logger log = LoggerFactory.getLogger(DevUsersConfig.class);

	@Bean
	InMemoryUserDetailsManager devUsers(
			@Value("${roster.dev-users.password:}") String configuredPassword,
			PasswordEncoder passwordEncoder) {
		String password = resolvePassword(configuredPassword);
		String encoded = passwordEncoder.encode(password);
		return new InMemoryUserDetailsManager(
				user("admin", encoded, "ADMIN"),
				user("reception", encoded, "RECEPTION"),
				user("staff1", encoded, "STAFF"),
				user("staff2", encoded, "STAFF"));
	}

	static String resolvePassword(String configuredPassword) {
		if (configuredPassword != null && !configuredPassword.isBlank()) {
			return configuredPassword;
		}
		String generated = UUID.randomUUID().toString();
		log.warn("ROSTER_DEV_PASSWORD not set; dev users share generated password: {}", generated);
		return generated;
	}

	private static UserDetails user(String username, String encodedPassword, String role) {
		return User.withUsername(username).password(encodedPassword).roles(role).build();
	}

}
