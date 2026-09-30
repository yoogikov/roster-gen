/**
 * Roster generation: a greedy generator that fills the hardest-to-staff shifts
 * first, followed by a repair pass of small swaps.
 *
 * <p>Layer: planning and persistence. Owner: Yoogi (proposed).
 *
 * <p>May depend on: {@code constraints}, {@code domain}. Spring-free.
 */
package com.iitm.roster.scheduler;
