package com.iitm.roster.export;

import com.iitm.roster.domain.Assignment;
import com.iitm.roster.domain.Roster;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

/**
 * CSV export of one roster: one row per assignment. PDF is not implemented; that is
 * Yoogi's later {@code PdfExporter}.
 */
@Component
public final class CsvRosterExporter implements RosterExporter {

	@Override
	public byte[] export(Roster roster, Format format) {
		if (format != Format.CSV) {
			throw new IllegalArgumentException(format + " is not supported");
		}
		StringBuilder csv = new StringBuilder("date,shift,staffId,name,post\n");
		for (Assignment assignment : roster.assignments()) {
			csv.append(assignment.slot().date()).append(',')
					.append(assignment.slot().shift().code()).append(',')
					.append(assignment.staff().id()).append(',')
					.append(escape(assignment.staff().name())).append(',')
					.append(assignment.post()).append('\n');
		}
		return csv.toString().getBytes(StandardCharsets.UTF_8);
	}

	private static String escape(String value) {
		if (value.indexOf(',') < 0 && value.indexOf('"') < 0) {
			return value;
		}
		return '"' + value.replace("\"", "\"\"") + '"';
	}

}
