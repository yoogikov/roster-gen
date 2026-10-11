package com.iitm.roster.constraints;

/** HARD constraints must hold; SOFT ones only make a roster worse when broken. */
public enum Severity {
	HARD,
	SOFT
}
