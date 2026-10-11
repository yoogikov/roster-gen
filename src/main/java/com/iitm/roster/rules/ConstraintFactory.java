package com.iitm.roster.rules;

import com.iitm.roster.constraints.Constraint;
import java.util.List;

/** Turns the rules the CMO saved back into constraints. */
public interface ConstraintFactory {

	/** Builds the constraint {@code spec} describes; throws if the spec is not valid. */
	Constraint build(RuleSpec spec) throws InvalidRuleException;

	/** The rule shapes the CMO may choose from. */
	List<RuleTemplate> templates();

}
