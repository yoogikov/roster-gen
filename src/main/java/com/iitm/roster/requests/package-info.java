/**
 * Leave and swap requests and their approval state machine, including swaps that
 * become {@code LAPSED} when still pending at shift start (roster left untouched).
 *
 * <p>Layer: application. Owner: Tushar (proposed).
 *
 * <p>May depend on: {@code scheduler}, {@code rules}, {@code repo}, {@code export}.
 */
package com.iitm.roster.requests;
