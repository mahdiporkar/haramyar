package ir.haramyar.rozeh.domain.policy;

import ir.haramyar.rozeh.domain.model.BookingRuleViolation;
import ir.haramyar.rozeh.domain.model.RozehBooking;

/**
 * Cross-aggregate rule: a provider's confirmed bookings must not overlap. This check gives an early, explicit error;
 * the guarantee under concurrency is the database exclusion constraint on confirmed provider slots, so two
 * simultaneous confirmations can never both commit.
 */
public final class BookingAvailabilityPolicy {
  private final ProviderCalendar calendar;

  public BookingAvailabilityPolicy(ProviderCalendar calendar) { this.calendar = calendar; }

  public void ensureProviderAvailable(RozehBooking booking) {
    booking.providerId().ifPresent(provider -> {
      if (calendar.hasConfirmedOverlap(provider, booking.time(), booking.id()))
        throw new BookingRuleViolation(BookingRuleViolation.Code.PROVIDER_SCHEDULE_CONFLICT, "The provider already has a confirmed ceremony in this time slot");
    });
  }
}
