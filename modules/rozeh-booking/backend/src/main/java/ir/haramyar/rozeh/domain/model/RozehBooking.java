package ir.haramyar.rozeh.domain.model;

import ir.haramyar.rozeh.domain.event.BookingEvent;
import ir.haramyar.rozeh.domain.policy.BookingPolicies;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Aggregate root of the Rozeh Booking context: one host's request for a religious ceremony at home, from request to
 * completion. All state changes go through intention-revealing methods that enforce the lifecycle and record an
 * auditable history entry and a domain event; there are no setters.
 */
public final class RozehBooking {
  private final BookingId id;
  private final TrackingCode trackingCode;
  private final HostId hostId;
  private final HostContact host;
  private final CeremonyLocation location;
  private final CeremonyDetails details;
  private final CeremonyTime time;
  private final Instant requestedAt;
  private BookingStatus status;
  private ProviderId providerId;
  private Instant updatedAt;
  private final long version;
  private final List<BookingHistoryEntry> history;
  private final List<BookingEvent> pendingEvents = new ArrayList<>();

  private RozehBooking(BookingId id, TrackingCode trackingCode, HostId hostId, HostContact host, CeremonyLocation location, CeremonyDetails details,
      CeremonyTime time, Instant requestedAt, BookingStatus status, ProviderId providerId, Instant updatedAt, long version, List<BookingHistoryEntry> history) {
    this.id = Objects.requireNonNull(id, "id");
    this.trackingCode = Objects.requireNonNull(trackingCode, "trackingCode");
    this.hostId = Objects.requireNonNull(hostId, "hostId");
    this.host = Objects.requireNonNull(host, "host");
    this.location = Objects.requireNonNull(location, "location");
    this.details = Objects.requireNonNull(details, "details");
    this.time = Objects.requireNonNull(time, "time");
    this.requestedAt = Objects.requireNonNull(requestedAt, "requestedAt");
    this.status = Objects.requireNonNull(status, "status");
    this.providerId = providerId;
    this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    this.version = version;
    this.history = new ArrayList<>(history);
  }

  /** A host submits a new request. New bookings always start as {@link BookingStatus#REQUESTED}. */
  public static RozehBooking request(BookingId id, TrackingCode trackingCode, BookingActor requester, HostContact host, CeremonyLocation location,
      CeremonyDetails details, CeremonyTime time, BookingPolicies policies, Instant now) {
    Objects.requireNonNull(requester, "requester");
    if (!policies.actors().permits(BookingAction.REQUEST, requester.role()))
      throw new BookingRuleViolation(BookingRuleViolation.Code.ACTOR_NOT_PERMITTED, "Only hosts submit ceremony requests");
    if (!time.start().isAfter(now))
      throw new BookingRuleViolation(BookingRuleViolation.Code.CEREMONY_TIME_NOT_IN_FUTURE, "The proposed ceremony time must be in the future");
    if (!policies.catalog().allows(details))
      throw new BookingRuleViolation(BookingRuleViolation.Code.CEREMONY_DETAILS_NOT_ALLOWED, "The ceremony category, audience or delivery mode is not offered");
    HostId hostId = new HostId(requester.userId());
    var booking = new RozehBooking(id, trackingCode, hostId, host, location, details, time, now, BookingStatus.REQUESTED, null, now, 0, List.of());
    booking.history.add(new BookingHistoryEntry(BookingAction.REQUEST, null, BookingStatus.REQUESTED, requester.userId(), requester.role(), null, now));
    booking.pendingEvents.add(new BookingEvent.BookingRequested(UUID.randomUUID(), id, hostId, trackingCode, time.start(), time.end(), now));
    return booking;
  }

  /** Rebuilds a persisted booking; records no history and no events. */
  public static RozehBooking reconstitute(BookingId id, TrackingCode trackingCode, HostId hostId, HostContact host, CeremonyLocation location, CeremonyDetails details,
      CeremonyTime time, Instant requestedAt, BookingStatus status, ProviderId providerId, Instant updatedAt, long version, List<BookingHistoryEntry> history) {
    return new RozehBooking(id, trackingCode, hostId, host, location, details, time, requestedAt, status, providerId, updatedAt, version, history);
  }

  public void accept(BookingActor actor, String note, BookingPolicies policies, Instant now) {
    authorize(BookingAction.ACCEPT, actor, policies);
    transition(BookingAction.ACCEPT, BookingStatus.ACCEPTED, actor, optionalNote(note), now);
    pendingEvents.add(new BookingEvent.BookingAccepted(UUID.randomUUID(), id, hostId, trackingCode, actor.userId(), now));
  }

  public void reject(BookingActor actor, String reason, BookingPolicies policies, Instant now) {
    authorize(BookingAction.REJECT, actor, policies);
    String detail = Text.required(reason, "Rejection reason", 3, 500);
    transition(BookingAction.REJECT, BookingStatus.REJECTED, actor, detail, now);
    pendingEvents.add(new BookingEvent.BookingRejected(UUID.randomUUID(), id, hostId, trackingCode, actor.userId(), now));
  }

  /** Assigns (or reassigns) the provider while the booking is accepted and not yet confirmed. */
  public void assignProvider(BookingActor actor, ProviderId provider, BookingPolicies policies, Instant now) {
    authorize(BookingAction.ASSIGN_PROVIDER, actor, policies);
    Objects.requireNonNull(provider, "provider");
    if (status != BookingStatus.ACCEPTED)
      throw new BookingRuleViolation(BookingRuleViolation.Code.INVALID_TRANSITION, "A provider can be assigned only to an accepted booking before confirmation");
    if (provider.equals(providerId)) return;
    providerId = provider;
    updatedAt = now;
    history.add(new BookingHistoryEntry(BookingAction.ASSIGN_PROVIDER, status, status, actor.userId(), actor.role(), provider.toString(), now));
    pendingEvents.add(new BookingEvent.ProviderAssigned(UUID.randomUUID(), id, hostId, trackingCode, provider, now));
  }

  /**
   * Final confirmation of an accepted booking. Provider availability across bookings is checked by the application
   * with {@link ir.haramyar.rozeh.domain.policy.BookingAvailabilityPolicy} and guaranteed by the database.
   */
  public void confirm(BookingActor actor, BookingPolicies policies, Instant now) {
    authorize(BookingAction.CONFIRM, actor, policies);
    if (status == BookingStatus.ACCEPTED && time.hasStartedAt(now))
      throw new BookingRuleViolation(BookingRuleViolation.Code.CEREMONY_ALREADY_STARTED, "A ceremony that has started can no longer be confirmed");
    if (status == BookingStatus.ACCEPTED && policies.confirmation().providerRequired() && providerId == null)
      throw new BookingRuleViolation(BookingRuleViolation.Code.PROVIDER_REQUIRED, "A provider must be assigned before confirmation");
    transition(BookingAction.CONFIRM, BookingStatus.CONFIRMED, actor, null, now);
    pendingEvents.add(new BookingEvent.BookingConfirmed(UUID.randomUUID(), id, hostId, trackingCode, providerId, time.start(), time.end(), now));
  }

  public void cancel(BookingActor actor, String reason, BookingPolicies policies, Instant now) {
    authorize(BookingAction.CANCEL, actor, policies);
    if (status.canTransitionTo(BookingStatus.CANCELLED)) {
      if (time.hasStartedAt(now))
        throw new BookingRuleViolation(BookingRuleViolation.Code.CEREMONY_ALREADY_STARTED, "A ceremony that has started can no longer be cancelled");
      if (policies.cancellation().deadlinePassed(actor.role(), time, now))
        throw new BookingRuleViolation(BookingRuleViolation.Code.CANCELLATION_DEADLINE_PASSED, "The cancellation deadline for this ceremony has passed");
    }
    String detail = actor.role() == BookingActor.Role.COORDINATOR ? Text.required(reason, "Cancellation reason", 3, 500) : optionalNote(reason);
    transition(BookingAction.CANCEL, BookingStatus.CANCELLED, actor, detail, now);
    pendingEvents.add(new BookingEvent.BookingCancelled(UUID.randomUUID(), id, hostId, trackingCode, actor.role(), now));
  }

  /** Marks a confirmed ceremony as held. Only possible once its start time has been reached, and only once. */
  public void complete(BookingActor actor, BookingPolicies policies, Instant now) {
    authorize(BookingAction.COMPLETE, actor, policies);
    if (status == BookingStatus.CONFIRMED && !time.hasStartedAt(now))
      throw new BookingRuleViolation(BookingRuleViolation.Code.CEREMONY_NOT_STARTED, "A ceremony can be completed only after it has started");
    transition(BookingAction.COMPLETE, BookingStatus.COMPLETED, actor, null, now);
    pendingEvents.add(new BookingEvent.BookingCompleted(UUID.randomUUID(), id, hostId, trackingCode, now));
  }

  public boolean isOwnedBy(UUID userId) {
    return hostId.value().equals(userId);
  }

  private void authorize(BookingAction action, BookingActor actor, BookingPolicies policies) {
    Objects.requireNonNull(actor, "actor");
    if (!policies.actors().permits(action, actor.role()))
      throw new BookingRuleViolation(BookingRuleViolation.Code.ACTOR_NOT_PERMITTED, actor.role() + " may not perform " + action);
    if (actor.role() == BookingActor.Role.HOST && !isOwnedBy(actor.userId()))
      throw new BookingRuleViolation(BookingRuleViolation.Code.NOT_BOOKING_OWNER, "Hosts may act only on their own bookings");
  }

  private void transition(BookingAction action, BookingStatus next, BookingActor actor, String detail, Instant now) {
    if (!status.canTransitionTo(next))
      throw new BookingRuleViolation(BookingRuleViolation.Code.INVALID_TRANSITION, "A " + status + " booking cannot become " + next);
    BookingStatus previous = status;
    status = next;
    updatedAt = now;
    history.add(new BookingHistoryEntry(action, previous, next, actor.userId(), actor.role(), detail, now));
  }

  private static String optionalNote(String note) {
    return Text.optional(note, "Note", 500);
  }

  /** Returns and clears the events recorded since the last call. */
  public List<BookingEvent> pullEvents() {
    var events = List.copyOf(pendingEvents);
    pendingEvents.clear();
    return events;
  }

  public BookingId id() { return id; }
  public TrackingCode trackingCode() { return trackingCode; }
  public HostId hostId() { return hostId; }
  public HostContact host() { return host; }
  public CeremonyLocation location() { return location; }
  public CeremonyDetails details() { return details; }
  public CeremonyTime time() { return time; }
  public Instant requestedAt() { return requestedAt; }
  public BookingStatus status() { return status; }
  public Optional<ProviderId> providerId() { return Optional.ofNullable(providerId); }
  public Instant updatedAt() { return updatedAt; }
  public long version() { return version; }
  public List<BookingHistoryEntry> history() { return List.copyOf(history); }
}
