package ir.haramyar.rozeh.application;

import ir.haramyar.rozeh.application.command.BookingCommands.AcceptBooking;
import ir.haramyar.rozeh.application.command.BookingCommands.AssignProvider;
import ir.haramyar.rozeh.application.command.BookingCommands.CancelBooking;
import ir.haramyar.rozeh.application.command.BookingCommands.CompleteBooking;
import ir.haramyar.rozeh.application.command.BookingCommands.ConfirmBooking;
import ir.haramyar.rozeh.application.command.BookingCommands.RejectBooking;
import ir.haramyar.rozeh.application.command.BookingCommands.RequestBooking;
import ir.haramyar.rozeh.application.port.BookingRepository;
import ir.haramyar.rozeh.application.port.ConcurrentModification;
import ir.haramyar.rozeh.application.port.IntegrationEvents;
import ir.haramyar.rozeh.application.port.PhoneNumbers;
import ir.haramyar.rozeh.application.port.ProviderRepository;
import ir.haramyar.rozeh.application.port.TrackingCodes;
import ir.haramyar.rozeh.application.query.BookingView;
import ir.haramyar.rozeh.domain.model.BookingActor;
import ir.haramyar.rozeh.domain.model.BookingId;
import ir.haramyar.rozeh.domain.model.BookingRuleViolation;
import ir.haramyar.rozeh.domain.model.CeremonyDetails;
import ir.haramyar.rozeh.domain.model.CeremonyLocation;
import ir.haramyar.rozeh.domain.model.CeremonyTime;
import ir.haramyar.rozeh.domain.model.HostContact;
import ir.haramyar.rozeh.domain.model.HostId;
import ir.haramyar.rozeh.domain.model.PhoneNumber;
import ir.haramyar.rozeh.domain.model.ProviderId;
import ir.haramyar.rozeh.domain.model.RozehBooking;
import ir.haramyar.rozeh.domain.model.TrackingCode;
import ir.haramyar.rozeh.domain.policy.BookingAvailabilityPolicy;
import ir.haramyar.rozeh.domain.policy.BookingPolicies;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Command handlers. Each handler validates the application boundary, resolves the actor, loads the aggregate, invokes
 * domain behavior, persists the change together with its outbox records in one transaction and returns a view.
 * Transition rules live in the aggregate and its policies, never here.
 */
public class BookingCommandHandlers {
  private static final int MAX_DURATION_MINUTES = 24 * 60;
  private final BookingRepository bookings;
  private final ProviderRepository providers;
  private final IntegrationEvents events;
  private final PhoneNumbers phoneNumbers;
  private final TrackingCodes trackingCodes;
  private final BookingAvailabilityPolicy availability;
  private final BookingPolicies policies;
  private final BookingAdmissionPolicy admission;
  private final BookingViews views;
  private final TransactionTemplate transactions;
  private final Clock clock;

  public BookingCommandHandlers(BookingRepository bookings, ProviderRepository providers, IntegrationEvents events, PhoneNumbers phoneNumbers,
      TrackingCodes trackingCodes, BookingAvailabilityPolicy availability, BookingPolicies policies, BookingAdmissionPolicy admission,
      BookingViews views, TransactionTemplate transactions, Clock clock) {
    this.bookings = bookings;
    this.providers = providers;
    this.events = events;
    this.phoneNumbers = phoneNumbers;
    this.trackingCodes = trackingCodes;
    this.availability = availability;
    this.policies = policies;
    this.admission = admission;
    this.views = views;
    this.transactions = transactions;
    this.clock = clock;
  }

  /** Idempotent per (host, clientRequestId): duplicate submissions, including concurrent ones, return the same booking. */
  public BookingView handle(RequestBooking command) {
    Objects.requireNonNull(command.clientRequestId(), "clientRequestId is required");
    if (command.startsAt() == null || command.timeZone() == null) throw new BookingRuleViolation(BookingRuleViolation.Code.INVALID_VALUE, "Ceremony start and time zone are required");
    if (command.durationMinutes() < 1 || command.durationMinutes() > MAX_DURATION_MINUTES) throw new BookingRuleViolation(BookingRuleViolation.Code.INVALID_VALUE, "Duration must be between 1 and 1440 minutes");
    HostId host = new HostId(command.actor().userId());
    try {
      return transactions.execute(status -> {
        var existing = bookings.findByClientRequest(host, command.clientRequestId());
        if (existing.isPresent()) return views.of(existing.get(), command.actor());
        if (admission.requireVerifiedMobile() && !command.actorMobileVerified())
          throw new BookingRuleViolation(BookingRuleViolation.Code.MOBILE_VERIFICATION_REQUIRED, "A verified mobile number is required before submitting a request");
        PhoneNumber phone = phoneNumbers.normalize(command.hostPhone())
            .orElseThrow(() -> new BookingRuleViolation(BookingRuleViolation.Code.INVALID_VALUE, "The contact number is not a valid mobile number"));
        Instant now = clock.instant();
        RozehBooking booking = RozehBooking.request(BookingId.newId(), uniqueTrackingCode(), command.actor(),
            new HostContact(command.hostName(), phone),
            new CeremonyLocation(command.city(), command.area(), command.address()),
            new CeremonyDetails(command.category(), command.audience(), command.deliveryMode(), command.approximateCapacity(), command.notes()),
            new CeremonyTime(command.startsAt(), command.startsAt().plus(Duration.ofMinutes(command.durationMinutes())), command.timeZone()),
            policies, now);
        bookings.add(booking, command.clientRequestId());
        publish(booking);
        return views.of(booking, command.actor());
      });
    } catch (BookingRepository.DuplicateClientRequest concurrentDuplicate) {
      return transactions.execute(status -> views.of(bookings.findByClientRequest(host, command.clientRequestId()).orElseThrow(BookingNotFound::new), command.actor()));
    }
  }

  public BookingView handle(AcceptBooking command) {
    return change(command.actor(), command.bookingId(), command.expectedVersion(), (booking, now) -> booking.accept(command.actor(), command.note(), policies, now));
  }

  public BookingView handle(RejectBooking command) {
    return change(command.actor(), command.bookingId(), command.expectedVersion(), (booking, now) -> booking.reject(command.actor(), command.reason(), policies, now));
  }

  public BookingView handle(AssignProvider command) {
    Objects.requireNonNull(command.providerId(), "providerId is required");
    return change(command.actor(), command.bookingId(), command.expectedVersion(), (booking, now) -> {
      var provider = providers.findById(new ProviderId(command.providerId()))
          .orElseThrow(() -> new BookingRuleViolation(BookingRuleViolation.Code.INVALID_VALUE, "Unknown provider"));
      provider.ensureAssignable();
      booking.assignProvider(command.actor(), provider.id(), policies, now);
      availability.ensureProviderAvailable(booking);
    });
  }

  public BookingView handle(ConfirmBooking command) {
    return change(command.actor(), command.bookingId(), command.expectedVersion(), (booking, now) -> {
      booking.confirm(command.actor(), policies, now);
      availability.ensureProviderAvailable(booking);
    });
  }

  public BookingView handle(CancelBooking command) {
    return change(command.actor(), command.bookingId(), command.expectedVersion(), (booking, now) -> booking.cancel(command.actor(), command.reason(), policies, now));
  }

  public BookingView handle(CompleteBooking command) {
    return change(command.actor(), command.bookingId(), command.expectedVersion(), (booking, now) -> booking.complete(command.actor(), policies, now));
  }

  private BookingView change(BookingActor actor, UUID bookingId, Long expectedVersion, BiConsumer<RozehBooking, Instant> behavior) {
    Objects.requireNonNull(actor, "actor");
    Objects.requireNonNull(bookingId, "bookingId");
    return transactions.execute(status -> {
      RozehBooking booking = bookings.findById(new BookingId(bookingId)).orElseThrow(BookingNotFound::new);
      // Hosts never learn whether someone else's booking exists.
      if (actor.role() == BookingActor.Role.HOST && !booking.isOwnedBy(actor.userId())) throw new BookingNotFound();
      if (expectedVersion != null && expectedVersion != booking.version()) throw new ConcurrentModification();
      behavior.accept(booking, clock.instant());
      bookings.update(booking);
      publish(booking);
      return views.of(bookings.findById(booking.id()).orElseThrow(), actor);
    });
  }

  private void publish(RozehBooking booking) {
    booking.pullEvents().forEach(event -> BookingIntegrationEvents.map(event).ifPresent(events::record));
  }

  private TrackingCode uniqueTrackingCode() {
    for (int attempt = 0; attempt < 8; attempt++) {
      TrackingCode code = trackingCodes.next();
      if (!bookings.trackingCodeExists(code)) return code;
    }
    throw new IllegalStateException("Could not allocate a unique tracking code");
  }
}
