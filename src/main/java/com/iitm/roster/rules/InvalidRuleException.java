package com.iitm.roster.rules;

/** A {@link RuleSpec} names an unknown template or has a missing or bad parameter. */
public class InvalidRuleException extends Exception {

	private static final long serialVersionUID = 1L;

	public InvalidRuleException(String message) {
		super(message);
	}

}
