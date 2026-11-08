package com.iitm.roster.service;

import com.iitm.roster.domain.HospitalTime;
import com.iitm.roster.domain.StandardShifts;
import com.iitm.roster.export.CsvRosterExporter;
import com.iitm.roster.export.Format;
import com.iitm.roster.export.PdfRosterExporter;
import com.iitm.roster.export.RosterExporter;
import com.iitm.roster.export.RosterExporters;
import com.iitm.roster.repo.MonthInputStore;
import com.iitm.roster.repo.RosterStore;
import com.iitm.roster.scheduler.HospitalRosterGenerator;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ServiceConfig {

	/** Injected rather than calling {@code Instant.now()}, so tests can fix the time. */
	@Bean
	@ConditionalOnMissingBean
	Clock clock() {
		return Clock.system(HospitalTime.ZONE);
	}

	@Bean
	HospitalRosterGenerator hospitalRosterGenerator() {
		return new HospitalRosterGenerator(StandardShifts.SHIFT_I, StandardShifts.SHIFT_II, StandardShifts.NIGHT);
	}

	/**
	 * CSV (the same exporter the CLI uses) and the printable PDF, behind one {@code RosterExporter}
	 * that picks by format.
	 */
	@Bean
	RosterExporter rosterExporter() {
		return new RosterExporters(Map.of(
				Format.CSV, new CsvRosterExporter(StandardShifts.SHIFT_I, StandardShifts.SHIFT_II, StandardShifts.NIGHT),
				Format.PDF, new PdfRosterExporter(StandardShifts.SHIFT_I, StandardShifts.SHIFT_II, StandardShifts.NIGHT)));
	}

	/**
	 * The hospital rules for a month, built from the input its roster was generated from, with
	 * the previous month's last nights carried in (post-duty off on the 1st). A month with no
	 * stored input (a roster not made by the generator) has no rules.
	 */
	@Bean
	ConstraintSource constraintSource(MonthInputStore inputs, RosterStore rosters, HospitalRosterGenerator generator) {
		return month -> inputs.findByMonth(month)
				.map(MonthInputs::fromStored)
				.map(input -> MonthInputs.withCarriedIn(input, rosters, inputs, generator.night()))
				.map(generator::constraints)
				.orElse(List.of());
	}

}
