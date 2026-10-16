package ir.haramyar.rozeh.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Who performs a booking operation, in which capacity. The capacity is established outside the domain from the secured
 * request (a coordinator capacity requires both the Hive business permission and a staff account); which capacity may
 * perform which operation is decided by {@link ir.haramyar.rozeh.domain.policy.BookingActorPolicy}.
 */
public record BookingActor(UUID userId, Role role) {
  public enum Role { HOST, COORDINATOR }

  public BookingActor {
    Objects.requireNonNull(userId, "userId");
    Objects.requireNonNull(role, "role");
  }

  public static BookingActor host(UUID userId) { return new BookingActor(userId, Role.HOST); }
  public static BookingActor coordinator(UUID userId) { return new BookingActor(userId, Role.COORDINATOR); }
}
