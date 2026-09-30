/**
 * Login and authorization. {@code AuthorizationPolicy} decides Allow, Deny or
 * RequiresApproval for an actor, action and roster (e.g. editing a published roster,
 * moving someone else's shift, changing past dates).
 *
 * <p>Layer: application. Owner: Tushar (proposed).
 *
 * <p>May depend on: {@code scheduler}, {@code rules}, {@code repo}, {@code export}.
 */
package com.iitm.roster.security;
