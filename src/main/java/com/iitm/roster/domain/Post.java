package com.iitm.roster.domain;

/**
 * What a doctor does on a shift. Each day shift has one ward doctor and one casualty
 * doctor; everyone else on the shift is general duty.
 */
public enum Post {
	WARD,
	CASUALTY,
	GENERAL
}
