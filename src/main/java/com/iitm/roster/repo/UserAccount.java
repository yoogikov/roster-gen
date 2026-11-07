package com.iitm.roster.repo;

/**
 * A login stored in the database. The password is already hashed. {@code staffId} is set only
 * for STAFF accounts that act as a particular doctor (null or blank otherwise).
 */
public record UserAccount(String username, String passwordHash, String role, String staffId, boolean enabled) {
}
