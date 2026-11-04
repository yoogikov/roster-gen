package com.iitm.roster.security;

import com.iitm.roster.domain.Actor;
import com.iitm.roster.domain.Role;
import java.util.Collection;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** The signed-in user, as an {@link Actor} the policy and services can take. */
@Component
public class CurrentActor {

	public Actor get() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()) {
			throw new IllegalStateException("no signed-in user");
		}
		Optional<String> staffId = Optional.empty();
		if (authentication.getPrincipal() instanceof RosterUser user) {
			staffId = user.staffId();
		}
		return new Actor(authentication.getName(), role(authentication), staffId);
	}

	private static Role role(Authentication authentication) {
		Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
		if (authorities == null) {
			throw new IllegalStateException("signed-in user has no role");
		}
		for (GrantedAuthority authority : authorities) {
			if (authority == null) {
				continue;
			}
			String name = authority.getAuthority();
			if (name != null && name.startsWith("ROLE_")) {
				return Role.valueOf(name.substring("ROLE_".length()));
			}
		}
		throw new IllegalStateException("signed-in user has no role");
	}

}
