package ir.haramyar.rozeh.domain.policy;

import ir.haramyar.rozeh.domain.model.BookingId;
import ir.haramyar.rozeh.domain.model.CeremonyTime;
import ir.haramyar.rozeh.domain.model.ProviderId;

/** Read model of providers' confirmed commitments, used by {@link BookingAvailabilityPolicy}. */
public interface ProviderCalendar {
  boolean hasConfirmedOverlap(ProviderId provider, CeremonyTime time, BookingId excluding);
}
