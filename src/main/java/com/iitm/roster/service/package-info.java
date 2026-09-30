/**
 * Application services. {@code RosterService.applyEdit} is the only way a roster
 * changes: one transaction, soft warnings returned, exactly one audit event written.
 *
 * <p>Layer: application. Owner: Tushar (proposed).
 *
 * <p>May depend on: {@code scheduler}, {@code rules}, {@code repo}, {@code export}.
 */
package com.iitm.roster.service;
