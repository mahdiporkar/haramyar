package ir.haramyar.rozeh.infrastructure.persistence;

import ir.haramyar.rozeh.application.port.BookingRepository;
import ir.haramyar.rozeh.application.port.ConcurrentModification;
import ir.haramyar.rozeh.domain.model.BookingAction;
import ir.haramyar.rozeh.domain.model.BookingActor;
import ir.haramyar.rozeh.domain.model.BookingHistoryEntry;
import ir.haramyar.rozeh.domain.model.BookingId;
import ir.haramyar.rozeh.domain.model.BookingRuleViolation;
import ir.haramyar.rozeh.domain.model.BookingStatus;
import ir.haramyar.rozeh.domain.model.CeremonyDetails;
import ir.haramyar.rozeh.domain.model.CeremonyLocation;
import ir.haramyar.rozeh.domain.model.CeremonyTime;
import ir.haramyar.rozeh.domain.model.HostContact;
import ir.haramyar.rozeh.domain.model.HostId;
import ir.haramyar.rozeh.domain.model.PhoneNumber;
import ir.haramyar.rozeh.domain.model.ProviderId;
import ir.haramyar.rozeh.domain.model.RozehBooking;
import ir.haramyar.rozeh.domain.model.TrackingCode;
import ir.haramyar.rozeh.domain.policy.ProviderCalendar;
import java.sql.SQLException;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

/** Maps the {@link RozehBooking} aggregate to its tables; never exposes JPA entities beyond this package. */
@Repository
class JpaBookingRepository implements BookingRepository, ProviderCalendar {
  private final BookingJpaRepository rows;
  private final BookingHistoryJpaRepository historyRows;

  JpaBookingRepository(BookingJpaRepository rows, BookingHistoryJpaRepository historyRows) {
    this.rows = rows;
    this.historyRows = historyRows;
  }

  @Override
  public Optional<RozehBooking> findById(BookingId id) {
    return rows.findById(id.value()).map(this::toDomain);
  }

  @Override
  public Optional<RozehBooking> findByClientRequest(HostId host, UUID clientRequestId) {
    return rows.findByHostIdAndClientRequestId(host.value(), clientRequestId).map(this::toDomain);
  }

  @Override
  public boolean trackingCodeExists(TrackingCode code) {
    return rows.existsByTrackingCode(code.value());
  }

  @Override
  public void add(RozehBooking booking, UUID clientRequestId) {
    BookingEntity entity = new BookingEntity();
    entity.id = booking.id().value();
    entity.trackingCode = booking.trackingCode().value();
    entity.hostId = booking.hostId().value();
    entity.clientRequestId = clientRequestId;
    entity.requestedAt = booking.requestedAt();
    copyMutableState(booking, entity);
    try {
      rows.saveAndFlush(entity);
    } catch (DataIntegrityViolationException violation) {
      if (violated(violation, "booking_client_request_unique")) throw new DuplicateClientRequest();
      throw violation;
    }
    appendHistory(booking, 0);
  }

  @Override
  public void update(RozehBooking booking) {
    BookingEntity entity = rows.findById(booking.id().value()).orElseThrow(ConcurrentModification::new);
    if (entity.version != booking.version()) throw new ConcurrentModification();
    copyMutableState(booking, entity);
    int persisted = historyRows.findByBookingIdOrderBySequenceNo(entity.id).size();
    try {
      rows.saveAndFlush(entity);
      appendHistory(booking, persisted);
      historyRows.flush();
    } catch (OptimisticLockingFailureException stale) {
      throw new ConcurrentModification();
    } catch (DataIntegrityViolationException violation) {
      if (violated(violation, "booking_provider_confirmed_no_overlap"))
        throw new BookingRuleViolation(BookingRuleViolation.Code.PROVIDER_SCHEDULE_CONFLICT, "The provider already has a confirmed ceremony in this time slot");
      throw violation;
    }
  }

  @Override
  public boolean hasConfirmedOverlap(ProviderId provider, CeremonyTime time, BookingId excluding) {
    return rows.confirmedOverlapExists(provider.value(), time.start(), time.end(), excluding.value());
  }

  private static void copyMutableState(RozehBooking booking, BookingEntity entity) {
    entity.hostName = booking.host().name();
    entity.hostPhone = booking.host().phone().e164();
    entity.city = booking.location().city();
    entity.area = booking.location().area();
    entity.address = booking.location().address();
    entity.category = booking.details().category();
    entity.audience = booking.details().audience();
    entity.deliveryMode = booking.details().deliveryMode();
    entity.approximateCapacity = booking.details().approximateCapacity();
    entity.notes = booking.details().notes();
    entity.startsAt = booking.time().start();
    entity.endsAt = booking.time().end();
    entity.timeZone = booking.time().zone().getId();
    entity.status = booking.status().name();
    entity.providerId = booking.providerId().map(ProviderId::value).orElse(null);
    entity.updatedAt = booking.updatedAt();
  }

  private void appendHistory(RozehBooking booking, int alreadyPersisted) {
    List<BookingHistoryEntry> history = booking.history();
    for (int i = alreadyPersisted; i < history.size(); i++) {
      BookingHistoryEntry entry = history.get(i);
      BookingHistoryEntity row = new BookingHistoryEntity();
      row.bookingId = booking.id().value();
      row.sequenceNo = i + 1;
      row.action = entry.action().name();
      row.fromStatus = entry.from() == null ? null : entry.from().name();
      row.toStatus = entry.to().name();
      row.actorId = entry.actorId();
      row.actorRole = entry.actorRole().name();
      row.detail = entry.detail();
      row.occurredAt = entry.at();
      historyRows.save(row);
    }
  }

  private RozehBooking toDomain(BookingEntity e) {
    List<BookingHistoryEntry> history = historyRows.findByBookingIdOrderBySequenceNo(e.id).stream()
        .map(h -> new BookingHistoryEntry(BookingAction.valueOf(h.action), h.fromStatus == null ? null : BookingStatus.valueOf(h.fromStatus),
            BookingStatus.valueOf(h.toStatus), h.actorId, BookingActor.Role.valueOf(h.actorRole), h.detail, h.occurredAt))
        .toList();
    return RozehBooking.reconstitute(new BookingId(e.id), new TrackingCode(e.trackingCode), new HostId(e.hostId),
        new HostContact(e.hostName, new PhoneNumber(e.hostPhone)), new CeremonyLocation(e.city, e.area, e.address),
        new CeremonyDetails(e.category, e.audience, e.deliveryMode, e.approximateCapacity, e.notes),
        new CeremonyTime(e.startsAt, e.endsAt, ZoneId.of(e.timeZone)), e.requestedAt, BookingStatus.valueOf(e.status),
        e.providerId == null ? null : new ProviderId(e.providerId), e.updatedAt, e.version, history);
  }

  static boolean violated(DataIntegrityViolationException violation, String constraint) {
    for (Throwable cause = violation; cause != null; cause = cause.getCause()) {
      if (cause instanceof org.hibernate.exception.ConstraintViolationException hibernate && constraint.equals(hibernate.getConstraintName())) return true;
      if (cause instanceof SQLException sql && sql.getMessage() != null && sql.getMessage().contains(constraint)) return true;
    }
    return false;
  }
}
