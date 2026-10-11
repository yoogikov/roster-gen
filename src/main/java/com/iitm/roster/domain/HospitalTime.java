package com.iitm.roster.domain;

import java.time.ZoneId;

/** The hospital's time zone. Every date and time in the roster is read in this zone. */
public final class HospitalTime {

	public static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

	private HospitalTime() {
	}

}
