package ir.haramyar.rozeh.infrastructure.persistence;

import ir.haramyar.rozeh.application.port.BookingReadModel;
import ir.haramyar.rozeh.application.query.BookingSummary;
import ir.haramyar.rozeh.application.query.Page;
import ir.haramyar.rozeh.application.query.ProviderSlot;
import ir.haramyar.rozeh.domain.model.BookingStatus;
import ir.haramyar.rozeh.domain.model.HostId;
import ir.haramyar.rozeh.domain.model.ProviderId;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
class JpaBookingReadModel implements BookingReadModel {
  private static final Instant EARLIEST = Instant.parse("2000-01-01T00:00:00Z");
  private static final Instant LATEST = Instant.parse("2200-01-01T00:00:00Z");
  private final BookingJpaRepository rows;
  private final ProviderJpaRepository providers;

  JpaBookingReadModel(BookingJpaRepository rows, ProviderJpaRepository providers) {
    this.rows = rows;
    this.providers = providers;
  }

  @Override
  public Page<BookingSummary> byHost(HostId host, Set<BookingStatus> statuses, int page, int size) {
    var result = rows.byHost(host.value(), statuses.isEmpty(), names(statuses), PageRequest.of(page, size));
    return new Page<>(summaries(result.getContent()), page, size, result.getTotalElements());
  }

  @Override
  public Page<BookingSummary> forCoordination(Set<BookingStatus> statuses, String city, Instant from, Instant to, int page, int size) {
    var result = rows.forCoordination(statuses.isEmpty(), names(statuses), city == null, city == null ? "" : city,
        from == null ? EARLIEST : from, to == null ? LATEST : to, PageRequest.of(page, size));
    return new Page<>(summaries(result.getContent()), page, size, result.getTotalElements());
  }

  @Override
  public List<ProviderSlot> confirmedSlots(ProviderId provider, Instant from, Instant to) {
    return rows.confirmedSlots(provider.value(), from, to).stream().map(b -> new ProviderSlot(b.id, b.trackingCode, b.startsAt, b.endsAt)).toList();
  }

  private static List<String> names(Set<BookingStatus> statuses) {
    return statuses.isEmpty() ? List.of("-") : statuses.stream().map(Enum::name).toList();
  }

  private List<BookingSummary> summaries(List<BookingEntity> entities) {
    Map<UUID, String> names = new HashMap<>();
    var ids = entities.stream().map(e -> e.providerId).filter(Objects::nonNull).distinct().toList();
    providers.findAllById(ids).forEach(p -> names.put(p.id, p.displayName));
    return entities.stream().map(e -> new BookingSummary(e.id, e.trackingCode, BookingStatus.valueOf(e.status), e.hostName, e.city, e.area, e.category, e.audience,
        e.startsAt, e.endsAt, e.timeZone, e.providerId, e.providerId == null ? null : names.get(e.providerId), e.requestedAt, e.updatedAt)).toList();
  }
}
