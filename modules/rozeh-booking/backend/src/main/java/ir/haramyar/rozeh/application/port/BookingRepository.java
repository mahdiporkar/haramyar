package ir.haramyar.rozeh.application.port;

import ir.haramyar.rozeh.domain.model.BookingId;
import ir.haramyar.rozeh.domain.model.HostId;
import ir.haramyar.rozeh.domain.model.RozehBooking;
import ir.haramyar.rozeh.domain.model.TrackingCode;
import java.util.Optional;
import java.util.UUID;

/** Persistence port for the {@link RozehBooking} aggregate. Implementations enforce optimistic concurrency. */
public interface BookingRepository {
  Optional<RozehBooking> findById(BookingId id);

  Optional<RozehBooking> findByClientRequest(HostId host, UUID clientRequestId);

  boolean trackingCodeExists(TrackingCode code);

  /**
   * Inserts a new booking.
   *
   * @throws DuplicateClientRequest when the host already submitted a request with this client request id
   */
  void add(RozehBooking booking, UUID clientRequestId);

  /**
   * Persists the aggregate's changes and appended history.
   *
   * @throws ConcurrentModification when the aggregate changed since it was loaded
   * @throws ir.haramyar.rozeh.domain.model.BookingRuleViolation with {@code PROVIDER_SCHEDULE_CONFLICT} when the
   *     database refuses an overlapping confirmed provider slot
   */
  void update(RozehBooking booking);

  final class DuplicateClientRequest extends RuntimeException {
    public DuplicateClientRequest() { super("Duplicate client request"); }
  }
}
