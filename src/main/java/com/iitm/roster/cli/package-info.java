/**
 * The command-line runner: a doctor list in, a month's roster out as CSV. Lets the
 * generator be used before the web layer exists, and is what the mid-demo runs live.
 *
 * <p>Layer: entry point. Owner: Yoogi.
 *
 * <p>May depend on: {@code export}, {@code scheduler}, {@code constraints}, {@code domain}.
 * Spring-free: it does not start the application or touch the database.
 */
package com.iitm.roster.cli;
