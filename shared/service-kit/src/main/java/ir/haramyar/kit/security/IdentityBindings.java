package ir.haramyar.kit.security;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Pins the first observed binding between an identity-provider subject and a canonical Hive user id. The BFF is the
 * only component that may send {@code X-Hive-User-Id}; pinning means that even a caller holding a valid token cannot
 * later present it with a different Hive user id (defence in depth behind network isolation).
 */
public class IdentityBindings {
  private final JdbcTemplate db;

  public IdentityBindings(JdbcTemplate db) { this.db = db; }

  /** @return true when the pair is new or matches the pinned binding */
  public boolean verify(String issuer, String subject, UUID hiveUserId) {
    db.update("insert into hive_identity_binding(issuer, subject, hive_user_id) values (?, ?, ?) on conflict (issuer, subject) do nothing", issuer, subject, hiveUserId);
    UUID pinned = db.queryForObject("select hive_user_id from hive_identity_binding where issuer = ? and subject = ?", UUID.class, issuer, subject);
    return hiveUserId.equals(pinned);
  }
}
