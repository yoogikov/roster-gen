/**
 * Leave and swap requests and their approval state machine, including swaps that
 * become {@code LAPSED} when still pending at shift start (roster left untouched).
 *
 * <p>Layer: application. Owner: Tushar (proposed).
 *
 * <p>May depend on: {@code scheduler}, {@code rules}, {@code repo}, {@code export}, and, in this
 * same layer, {@code service} ({@code RosterService.applyEdits} performs an approved swap)
 * and {@code security} ({@code AuthorizationPolicy} is asked before each transition).
 */
package com.iitm.roster.requests;
