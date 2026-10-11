package com.iitm.roster.service;

import com.iitm.roster.domain.HospitalTime;
import com.iitm.roster.domain.StandardShifts;
import com.iitm.roster.repo.MonthInputStore;
import com.iitm.roster.scheduler.HospitalRosterGenerator;
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

	@Bean
	HospitalRosterGenerator hospitalRosterGenerator() {
		return new HospitalRosterGenerator(StandardShifts.SHIFT_I, StandardShifts.SHIFT_II, StandardShifts.NIGHT);
	}

	/**
	 * The hospital rules for a month, built from the input its roster was generated from.
	 * A month with no stored input (a roster not made by the generator) has no rules.
	 */
	@Bean
	ConstraintSource constraintSource(MonthInputStore inputs, HospitalRosterGenerator generator) {
		return month -> inputs.findByMonth(month)
				.map(MonthInputs::fromStored)
				.map(generator::constraints)
				.orElse(List.of());
	}

}
