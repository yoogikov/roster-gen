package com.iitm.roster.service;

import com.iitm.roster.domain.HospitalTime;
import java.time.Clock;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ServiceConfig {

	/** Injected rather than calling {@code Instant.now()}, so tests can fix the time. */
	@Bean
	Clock clock() {
		return Clock.system(HospitalTime.ZONE);
	}

	/** No constraints until {@code rules} provides a {@link ConstraintSource}; edits only check double-booking. */
	@Bean
	ConstraintSource constraintSource() {
		return month -> List.of();
	}

}
