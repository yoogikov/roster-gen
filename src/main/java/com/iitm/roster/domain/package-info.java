/**
 * Core model of the duty roster: staff, shifts, slots, leave and the roster itself.
 *
 * <p>Layer: core (lowest). Owner: Yoogi (proposed).
 *
 * <p>May depend on: nothing except the JDK ({@code java.time} for dates, always in
 * {@code Asia/Kolkata}). No Spring, no JPA, no other {@code com.iitm.roster} package.
 */
package com.iitm.roster.domain;
