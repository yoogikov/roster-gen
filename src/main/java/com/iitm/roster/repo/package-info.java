/**
 * Persistence: JPA entities and Spring Data repositories (Staff, Roster, Request, Audit),
 * and mapping to and from {@code domain} types.
 *
 * <p>Only the {@code *Store} classes are public, and they take and return {@code domain}
 * records. Entities and Spring Data interfaces are package-private, so the compiler keeps
 * JPA types from leaking out of this package.
 *
 * <p>Layer: planning and persistence. Owner: Tushar (proposed).
 *
 * <p>May depend on: {@code constraints}, {@code domain}.
 */
package com.iitm.roster.repo;
