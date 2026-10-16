package ir.haramyar.kit.security;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * The authenticated caller of a business operation, derived only from the secured request: the canonical Hive user id
 * forwarded by the BFF ({@code X-Hive-User-Id}) bound to the identity-provider token the BFF injected. Browser-supplied
 * identifiers are never trusted.
 *
 * @param userId canonical Hive user id; the only key business data may be owned by
 * @param subject identity-provider subject of the forwarded token
 * @param username identity-provider username (the normalized mobile number for HaramYar customers); never a business key
 * @param displayName best available display name
 * @param realmRoles identity-provider realm roles (authentication classes such as staff, not business permissions)
 * @param mobileVerified whether ownership of the mobile number has been verified by an approved mechanism
 */
public record Actor(UUID userId, String subject, String username, String displayName, Set<String> realmRoles, boolean mobileVerified) {
  public Actor {
    Objects.requireNonNull(userId, "userId");
    Objects.requireNonNull(subject, "subject");
    realmRoles = Set.copyOf(realmRoles == null ? Set.of() : realmRoles);
  }

  public boolean hasRealmRole(String role) {
    return realmRoles.contains(role);
  }
}
