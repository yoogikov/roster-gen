package com.iitm.roster.service;

import com.iitm.roster.domain.Roster;
import com.iitm.roster.export.Format;
import com.iitm.roster.export.RosterExporter;
import com.iitm.roster.repo.RosterStore;
import java.time.YearMonth;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads a month and writes it out. Web calls this instead of {@code export} directly. */
@Service
@Transactional(readOnly = true)
public class RosterExportService {

	private final RosterStore rosters;
	private final RosterExporter exporter;

	public RosterExportService(RosterStore rosters, RosterExporter exporter) {
		this.rosters = rosters;
		this.exporter = exporter;
	}

	/**
	 * @throws IllegalArgumentException if the month has no roster, or {@code format} is not supported
	 */
	public byte[] export(YearMonth month, Format format) {
		Roster roster = rosters.findByMonth(month)
				.orElseThrow(() -> new IllegalArgumentException("no roster for " + month));
		return exporter.export(roster, format);
	}

}
