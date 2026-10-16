package ir.haramyar.rozeh.application;

import ir.haramyar.rozeh.application.port.ProviderRepository;
import ir.haramyar.rozeh.application.query.BookingView;
import ir.haramyar.rozeh.domain.model.BookingAction;
import ir.haramyar.rozeh.domain.model.BookingActor;
import ir.haramyar.rozeh.domain.model.BookingRuleViolation;
import ir.haramyar.rozeh.domain.model.ProviderId;
import ir.haramyar.rozeh.domain.model.RozehBooking;
import ir.haramyar.rozeh.domain.policy.BookingPolicies;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Consumer;

/** Builds {@link BookingView}s, including the operations the viewer may currently perform. */
public final class BookingViews {
  private final ProviderRepository providers;
  private final BookingPolicies policies;
  private final Clock clock;

  public BookingViews(ProviderRepository providers, BookingPolicies policies, Clock clock) {
    this.providers = providers;
    this.policies = policies;
    this.clock = clock;
  }

  public BookingView of(RozehBooking booking, BookingActor viewer) {
    BookingView.Provider provider = booking.providerId()
        .map(id -> new BookingView.Provider(id.value(), providers.findById(id).map(p -> p.displayName()).orElse(null)))
        .orElse(null);
    var history = booking.history().stream()
        .map(e -> new BookingView.History(e.action(), e.from(), e.to(), e.actorRole(), e.actorId().equals(viewer.userId()), e.detail(), e.at()))
        .toList();
    return new BookingView(booking.id().value(), booking.trackingCode().value(), booking.status(),
        new BookingView.Host(booking.hostId().value(), booking.host().name(), booking.host().phone().e164()),
        new BookingView.Location(booking.location().city(), booking.location().area(), booking.location().address()),
        new BookingView.Details(booking.details().category(), booking.details().audience(), booking.details().deliveryMode(), booking.details().approximateCapacity(), booking.details().notes()),
        new BookingView.Time(booking.time().start(), booking.time().end(), booking.time().zone().getId()),
        provider, booking.requestedAt(), booking.updatedAt(), booking.version(), history, allowedActions(booking, viewer));
  }

  /** Evaluates each operation against the real domain rules on a throwaway copy of the aggregate. */
  Set<BookingAction> allowedActions(RozehBooking booking, BookingActor viewer) {
    Instant now = clock.instant();
    var allowed = EnumSet.noneOf(BookingAction.class);
    probe(booking, allowed, BookingAction.ACCEPT, b -> b.accept(viewer, null, policies, now));
    probe(booking, allowed, BookingAction.REJECT, b -> b.reject(viewer, "capability probe", policies, now));
    probe(booking, allowed, BookingAction.ASSIGN_PROVIDER, b -> b.assignProvider(viewer, ProviderId.newId(), policies, now));
    probe(booking, allowed, BookingAction.CONFIRM, b -> b.confirm(viewer, policies, now));
    probe(booking, allowed, BookingAction.CANCEL, b -> b.cancel(viewer, "capability probe", policies, now));
    probe(booking, allowed, BookingAction.COMPLETE, b -> b.complete(viewer, policies, now));
    return allowed;
  }

  private static void probe(RozehBooking booking, Set<BookingAction> allowed, BookingAction action, Consumer<RozehBooking> attempt) {
    RozehBooking copy = RozehBooking.reconstitute(booking.id(), booking.trackingCode(), booking.hostId(), booking.host(), booking.location(), booking.details(),
        booking.time(), booking.requestedAt(), booking.status(), booking.providerId().orElse(null), booking.updatedAt(), booking.version(), booking.history());
    try {
      attempt.accept(copy);
      allowed.add(action);
    } catch (BookingRuleViolation notAllowed) {
      // not available to this viewer now
    }
  }
}
