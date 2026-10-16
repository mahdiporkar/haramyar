package ir.haramyar.rozeh.application;

import ir.haramyar.kit.messaging.IntegrationEvent;
import ir.haramyar.rozeh.application.port.BookingRepository;
import ir.haramyar.rozeh.application.port.ConcurrentModification;
import ir.haramyar.rozeh.application.port.IntegrationEvents;
import ir.haramyar.rozeh.application.port.ProviderRepository;
import ir.haramyar.rozeh.domain.model.BookingId;
import ir.haramyar.rozeh.domain.model.BookingStatus;
import ir.haramyar.rozeh.domain.model.CeremonyTime;
import ir.haramyar.rozeh.domain.model.HostId;
import ir.haramyar.rozeh.domain.model.Provider;
import ir.haramyar.rozeh.domain.model.ProviderId;
import ir.haramyar.rozeh.domain.model.RozehBooking;
import ir.haramyar.rozeh.domain.model.TrackingCode;
import ir.haramyar.rozeh.domain.policy.ProviderCalendar;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Test doubles for application-layer tests; persistence semantics (versions, uniqueness) are mimicked. */
final class InMemoryAdapters {
  private InMemoryAdapters() {}

  static final class Bookings implements BookingRepository, ProviderCalendar {
    final Map<BookingId, RozehBooking> rows = new HashMap<>();
    final Map<BookingId, Long> versions = new HashMap<>();
    final Map<String, BookingId> clientRequests = new HashMap<>();
    int updates;

    @Override public Optional<RozehBooking> findById(BookingId id) {
      RozehBooking b = rows.get(id);
      if (b == null) return Optional.empty();
      return Optional.of(RozehBooking.reconstitute(b.id(), b.trackingCode(), b.hostId(), b.host(), b.location(), b.details(), b.time(), b.requestedAt(),
          b.status(), b.providerId().orElse(null), b.updatedAt(), versions.get(id), b.history()));
    }

    @Override public Optional<RozehBooking> findByClientRequest(HostId host, UUID clientRequestId) {
      return Optional.ofNullable(clientRequests.get(host + "/" + clientRequestId)).flatMap(this::findById);
    }

    @Override public boolean trackingCodeExists(TrackingCode code) {
      return rows.values().stream().anyMatch(b -> b.trackingCode().equals(code));
    }

    @Override public void add(RozehBooking booking, UUID clientRequestId) {
      String key = booking.hostId() + "/" + clientRequestId;
      if (clientRequests.containsKey(key)) throw new DuplicateClientRequest();
      clientRequests.put(key, booking.id());
      rows.put(booking.id(), booking);
      versions.put(booking.id(), 0L);
    }

    @Override public void update(RozehBooking booking) {
      if (versions.get(booking.id()) != booking.version()) throw new ConcurrentModification();
      rows.put(booking.id(), booking);
      versions.put(booking.id(), booking.version() + 1);
      updates++;
    }

    @Override public boolean hasConfirmedOverlap(ProviderId provider, CeremonyTime time, BookingId excluding) {
      return rows.values().stream().anyMatch(b -> b.status() == BookingStatus.CONFIRMED && b.providerId().filter(provider::equals).isPresent()
          && !b.id().equals(excluding) && b.time().overlaps(time));
    }
  }

  static final class Providers implements ProviderRepository {
    final Map<ProviderId, Provider> rows = new HashMap<>();

    @Override public Optional<Provider> findById(ProviderId id) { return Optional.ofNullable(rows.get(id)); }
    @Override public List<Provider> list(boolean includeInactive) { return rows.values().stream().filter(p -> includeInactive || p.active()).toList(); }
    @Override public void add(Provider provider) { rows.put(provider.id(), provider); }
    @Override public void update(Provider provider) { rows.put(provider.id(), provider); }
  }

  static final class Events implements IntegrationEvents {
    final List<IntegrationEvent> recorded = new ArrayList<>();

    @Override public void record(IntegrationEvent event) { recorded.add(event); }
  }
}
