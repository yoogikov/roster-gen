/**
 * User-authored rules: the CMO picks from a fixed list of rule shapes, fills in
 * parameters, and the factory turns each {@code RuleSpec} into a {@code Constraint}.
 * Also holds per-date staffing overrides. There is no general rule language.
 *
 * <p>Layer: planning and persistence. Owner: Yoogi (proposed).
 *
 * <p>May depend on: {@code constraints}, {@code domain}. The evaluating half
 * (spec to constraint) is Spring-free.
 */
package com.iitm.roster.rules;
