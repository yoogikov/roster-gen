/**
 * Application services. {@code RosterService.applyEdit} is the only way a roster
 * changes: one transaction, soft warnings returned, exactly one audit event written.
 *
 * <p>Layer: application. Owner: Tushar (proposed).
 *
 * <p>May depend on: {@code scheduler}, {@code rules}, {@code repo}, {@code export}, and
 * the {@code constraints} and {@code domain} types they pass through (e.g. checking each
 * {@code Constraint} on an edit).
 */
package com.iitm.roster.service;
