package com.iitm.roster.export;

import com.iitm.roster.domain.Leave;
import com.iitm.roster.domain.Staff;
import java.util.List;

/**
 * The doctors to roster, in night-rotation order, and their leave.
 *
 * @param doctors in the order they take nights; the first does the first night of the month
 */
public record DoctorList(List<Staff> doctors, List<Leave> leaves) {

	public DoctorList {
		doctors = List.copyOf(doctors);
		leaves = List.copyOf(leaves);
	}

}
