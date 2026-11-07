package com.iitm.roster.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.iitm.roster.domain.Roster;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RosterExportersTest {

	private static final Roster OCTOBER = Roster.empty(YearMonth.of(2026, 10));

	@Test
	void eachFormatGoesToItsOwnExporter() {
		RosterExporters exporters = new RosterExporters(Map.of(
				Format.CSV, (roster, format) -> ("csv " + format).getBytes(StandardCharsets.UTF_8),
				Format.PDF, (roster, format) -> ("pdf " + format).getBytes(StandardCharsets.UTF_8)));

		assertThat(new String(exporters.export(OCTOBER, Format.CSV), StandardCharsets.UTF_8)).isEqualTo("csv CSV");
		assertThat(new String(exporters.export(OCTOBER, Format.PDF), StandardCharsets.UTF_8)).isEqualTo("pdf PDF");
	}

	@Test
	void aFormatWithNoExporterIsRefused() {
		RosterExporters csvOnly = new RosterExporters(Map.of(Format.CSV, (roster, format) -> new byte[0]));

		assertThatThrownBy(() -> csvOnly.export(OCTOBER, Format.PDF))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("PDF");
	}

}
