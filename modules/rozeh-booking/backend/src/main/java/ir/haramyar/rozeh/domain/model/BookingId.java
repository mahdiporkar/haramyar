package ir.haramyar.rozeh.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Immutable identity of a booking. */
public record BookingId(UUID value) {
  public BookingId { Objects.requireNonNull(value, "bookingId"); }

  public static BookingId newId() { return new BookingId(UUID.randomUUID()); }

  @Override public String toString() { return value.toString(); }
}
