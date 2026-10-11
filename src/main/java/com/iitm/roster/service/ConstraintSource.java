package com.iitm.roster.service;

import com.iitm.roster.constraints.Constraint;
import java.time.YearMonth;
import java.util.List;

/** The constraints that apply to one month's roster (per-date overrides make them month-specific). */
@FunctionalInterface
public interface ConstraintSource {

	List<Constraint> constraintsFor(YearMonth month);

}
