package com.iitm.roster.export;

import com.iitm.roster.domain.Roster;
import java.util.Map;
import java.util.Objects;

/** One exporter per format, behind the single {@link RosterExporter} the rest of the app sees. */
public final class RosterExporters implements RosterExporter {

	private final Map<Format, RosterExporter> byFormat;

	public RosterExporters(Map<Format, RosterExporter> byFormat) {
		this.byFormat = Map.copyOf(byFormat);
	}

	@Override
	public byte[] export(Roster roster, Format format) {
		Objects.requireNonNull(format, "format");
		RosterExporter exporter = byFormat.get(format);
		if (exporter == null) {
			throw new IllegalArgumentException("no exporter for " + format);
		}
		return exporter.export(roster, format);
	}

}
