package com.iitm.roster.export;

/** A doctor list that cannot be read; the message names the line and what is wrong with it. */
public class InvalidDoctorListException extends Exception {

	private static final long serialVersionUID = 1L;

	public InvalidDoctorListException(String message) {
		super(message);
	}

	public InvalidDoctorListException(String message, Throwable cause) {
		super(message, cause);
	}

}
