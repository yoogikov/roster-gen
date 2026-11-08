package com.iitm.roster.repo;

import com.iitm.roster.domain.Staff;
import com.iitm.roster.domain.StaffRole;

/**
 * A doctor (or nurse) in the hospital directory, including whether they are still active.
 * Rostering uses {@link Staff}; inactive people stay in old rosters but are not offered for
 * new months or new logins.
 */
public record StaffRecord(String id, String name, StaffRole role, boolean active) {

	public Staff toStaff() {
		return new Staff(id, name, role);
	}

}
