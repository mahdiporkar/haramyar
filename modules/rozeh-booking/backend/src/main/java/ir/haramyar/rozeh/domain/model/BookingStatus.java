package ir.haramyar.rozeh.domain.model;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Booking lifecycle (approved core state machine):
 * <pre>
 *   REQUESTED -> ACCEPTED -> CONFIRMED -> COMPLETED
 *   REQUESTED -> REJECTED
 *   REQUESTED | ACCEPTED | CONFIRMED -> CANCELLED   (subject to the cancellation policy)
 * </pre>
 * REJECTED, CANCELLED and COMPLETED are terminal; nothing returns to REQUESTED.
 */
public enum BookingStatus {
  REQUESTED, ACCEPTED, REJECTED, CONFIRMED, CANCELLED, COMPLETED;

  private static final Map<BookingStatus, Set<BookingStatus>> NEXT = Map.of(
      REQUESTED, EnumSet.of(ACCEPTED, REJECTED, CANCELLED),
      ACCEPTED, EnumSet.of(CONFIRMED, CANCELLED),
      CONFIRMED, EnumSet.of(COMPLETED, CANCELLED),
      REJECTED, EnumSet.noneOf(BookingStatus.class),
      CANCELLED, EnumSet.noneOf(BookingStatus.class),
      COMPLETED, EnumSet.noneOf(BookingStatus.class));

  public boolean canTransitionTo(BookingStatus next) {
    return NEXT.get(this).contains(next);
  }

  public boolean isTerminal() {
    return NEXT.get(this).isEmpty();
  }
}
