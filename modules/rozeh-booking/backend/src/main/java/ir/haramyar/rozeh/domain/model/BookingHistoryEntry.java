package ir.haramyar.rozeh.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable audit record of one meaningful change. {@code from} is null for the initial request; for operations that
 * keep the status (provider assignment) {@code from} equals {@code to}.
 */
public record BookingHistoryEntry(BookingAction action, BookingStatus from, BookingStatus to, UUID actorId, BookingActor.Role actorRole, String detail, Instant at) {
  public BookingHistoryEntry {
    Objects.requireNonNull(action, "action");
    Objects.requireNonNull(to, "to");
    Objects.requireNonNull(actorId, "actorId");
    Objects.requireNonNull(actorRole, "actorRole");
    Objects.requireNonNull(at, "at");
  }
}
