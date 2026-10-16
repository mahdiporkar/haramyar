package ir.haramyar.rozeh.domain.event;

import ir.haramyar.rozeh.domain.model.BookingActor;
import ir.haramyar.rozeh.domain.model.BookingId;
import ir.haramyar.rozeh.domain.model.HostId;
import ir.haramyar.rozeh.domain.model.ProviderId;
import ir.haramyar.rozeh.domain.model.TrackingCode;
import java.time.Instant;
import java.util.UUID;

/**
 * Facts recorded by {@link ir.haramyar.rozeh.domain.model.RozehBooking} as part of its behavior. Framework-free; the
 * application layer decides which of them become integration events.
 */
public sealed interface BookingEvent {
  UUID eventId();
  BookingId bookingId();
  HostId hostId();
  TrackingCode trackingCode();
  Instant occurredAt();

  record BookingRequested(UUID eventId, BookingId bookingId, HostId hostId, TrackingCode trackingCode, Instant ceremonyStart, Instant ceremonyEnd, Instant occurredAt) implements BookingEvent {}
  record BookingAccepted(UUID eventId, BookingId bookingId, HostId hostId, TrackingCode trackingCode, UUID acceptedBy, Instant occurredAt) implements BookingEvent {}
  record BookingRejected(UUID eventId, BookingId bookingId, HostId hostId, TrackingCode trackingCode, UUID rejectedBy, Instant occurredAt) implements BookingEvent {}
  record ProviderAssigned(UUID eventId, BookingId bookingId, HostId hostId, TrackingCode trackingCode, ProviderId providerId, Instant occurredAt) implements BookingEvent {}
  record BookingConfirmed(UUID eventId, BookingId bookingId, HostId hostId, TrackingCode trackingCode, ProviderId providerId, Instant ceremonyStart, Instant ceremonyEnd, Instant occurredAt) implements BookingEvent {}
  record BookingCancelled(UUID eventId, BookingId bookingId, HostId hostId, TrackingCode trackingCode, BookingActor.Role cancelledBy, Instant occurredAt) implements BookingEvent {}
  record BookingCompleted(UUID eventId, BookingId bookingId, HostId hostId, TrackingCode trackingCode, Instant occurredAt) implements BookingEvent {}
}
