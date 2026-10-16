package ir.haramyar.rozeh.application;

import ir.haramyar.kit.messaging.IntegrationEvent;
import ir.haramyar.rozeh.domain.event.BookingEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Maps domain events to the published contracts {@code haramyar.booking.<fact>.v1}. Payloads contain identifiers,
 * status facts and times only: no names, phone numbers or addresses. The canonical host id is included because the
 * notification consumer must address the host. {@link BookingEvent.ProviderAssigned} stays internal.
 */
public final class BookingIntegrationEvents {
  public static final String AGGREGATE = "RozehBooking";

  private BookingIntegrationEvents() {}

  public static Optional<IntegrationEvent> map(BookingEvent event) {
    var payload = new LinkedHashMap<String, Object>();
    payload.put("bookingId", event.bookingId().toString());
    payload.put("trackingCode", event.trackingCode().value());
    payload.put("hostId", event.hostId().toString());
    String fact = switch (event) {
      case BookingEvent.BookingRequested e -> { payload.put("ceremonyStartsAt", e.ceremonyStart().toString()); yield "requested"; }
      case BookingEvent.BookingAccepted e -> "accepted";
      case BookingEvent.BookingRejected e -> "rejected";
      case BookingEvent.BookingConfirmed e -> {
        payload.put("ceremonyStartsAt", e.ceremonyStart().toString());
        payload.put("ceremonyEndsAt", e.ceremonyEnd().toString());
        if (e.providerId() != null) payload.put("providerId", e.providerId().toString());
        yield "confirmed";
      }
      case BookingEvent.BookingCancelled e -> { payload.put("cancelledBy", e.cancelledBy().name()); yield "cancelled"; }
      case BookingEvent.BookingCompleted e -> "completed";
      case BookingEvent.ProviderAssigned e -> null;
    };
    if (fact == null) return Optional.empty();
    payload.put("occurredAt", event.occurredAt().toString());
    return Optional.of(new IntegrationEvent(event.eventId(), "haramyar.booking." + fact + ".v1", AGGREGATE, event.bookingId().toString(), event.occurredAt(), Map.copyOf(payload)));
  }
}
