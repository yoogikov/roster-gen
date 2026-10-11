package com.iitm.roster.export;

import com.iitm.roster.domain.Roster;

/** Writes a roster out as a file. */
public interface RosterExporter {

	/** The file contents; throws {@link IllegalArgumentException} if {@code format} is not supported. */
	byte[] export(Roster roster, Format format);

}
