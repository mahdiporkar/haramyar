package ir.haramyar.rozeh.application;

import ir.haramyar.rozeh.application.port.BookingReadModel;
import ir.haramyar.rozeh.application.port.BookingRepository;
import ir.haramyar.rozeh.application.query.BookingSummary;
import ir.haramyar.rozeh.application.query.BookingView;
import ir.haramyar.rozeh.application.query.Page;
import ir.haramyar.rozeh.application.query.ProviderSlot;
import ir.haramyar.rozeh.domain.model.BookingActor;
import ir.haramyar.rozeh.domain.model.BookingId;
import ir.haramyar.rozeh.domain.model.BookingStatus;
import ir.haramyar.rozeh.domain.model.HostId;
import ir.haramyar.rozeh.domain.model.ProviderId;
import ir.haramyar.rozeh.domain.model.RozehBooking;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.transaction.support.TransactionTemplate;

/** Query handlers: GetBookingDetails, GetMyBookings, SearchBookingsForCoordination, GetBookingStatusHistory, GetProviderAvailability. */
public class BookingQueryHandlers {
  private static final int MAX_PAGE_SIZE = 100;
  private final BookingRepository bookings;
  private final BookingReadModel readModel;
  private final BookingViews views;
  private final TransactionTemplate readOnly;

  public BookingQueryHandlers(BookingRepository bookings, BookingReadModel readModel, BookingViews views, TransactionTemplate readOnly) {
    this.bookings = bookings;
    this.readModel = readModel;
    this.views = views;
    this.readOnly = readOnly;
  }

  public BookingView getBookingDetails(BookingActor viewer, UUID bookingId) {
    return readOnly.execute(status -> views.of(visible(viewer, bookingId), viewer));
  }

  public List<BookingView.History> getBookingStatusHistory(BookingActor viewer, UUID bookingId) {
    return getBookingDetails(viewer, bookingId).history();
  }

  public Page<BookingSummary> getMyBookings(BookingActor host, Set<BookingStatus> statuses, int page, int size) {
    if (host.role() != BookingActor.Role.HOST) throw new IllegalArgumentException("Only hosts have own bookings");
    return readOnly.execute(status -> readModel.byHost(new HostId(host.userId()), statuses, Math.max(page, 0), clamp(size)));
  }

  public Page<BookingSummary> searchBookingsForCoordination(Set<BookingStatus> statuses, String city, Instant from, Instant to, int page, int size) {
    return readOnly.execute(status -> readModel.forCoordination(statuses, city == null || city.isBlank() ? null : city.strip(), from, to, Math.max(page, 0), clamp(size)));
  }

  public List<ProviderSlot> getProviderAvailability(UUID providerId, Instant from, Instant to) {
    if (from == null || to == null || !to.isAfter(from) || Duration.between(from, to).toDays() > 92) throw new IllegalArgumentException("Choose a range of at most 92 days");
    return readOnly.execute(status -> readModel.confirmedSlots(new ProviderId(providerId), from, to));
  }

  private RozehBooking visible(BookingActor viewer, UUID bookingId) {
    RozehBooking booking = bookings.findById(new BookingId(bookingId)).orElseThrow(BookingNotFound::new);
    if (viewer.role() == BookingActor.Role.HOST && !booking.isOwnedBy(viewer.userId())) throw new BookingNotFound();
    return booking;
  }

  private static int clamp(int size) { return Math.min(Math.max(size, 1), MAX_PAGE_SIZE); }
}
