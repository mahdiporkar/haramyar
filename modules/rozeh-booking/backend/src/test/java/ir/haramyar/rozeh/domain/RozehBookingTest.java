package ir.haramyar.rozeh.domain;

import static ir.haramyar.rozeh.domain.BookingFixtures.COORDINATOR;
import static ir.haramyar.rozeh.domain.BookingFixtures.HOST;
import static ir.haramyar.rozeh.domain.BookingFixtures.NOW;
import static ir.haramyar.rozeh.domain.BookingFixtures.OTHER_HOST;
import static ir.haramyar.rozeh.domain.BookingFixtures.coordinator;
import static ir.haramyar.rozeh.domain.BookingFixtures.host;
import static ir.haramyar.rozeh.domain.BookingFixtures.inDays;
import static ir.haramyar.rozeh.domain.BookingFixtures.policies;
import static ir.haramyar.rozeh.domain.BookingFixtures.requested;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ir.haramyar.rozeh.domain.event.BookingEvent;
import ir.haramyar.rozeh.domain.model.BookingAction;
import ir.haramyar.rozeh.domain.model.BookingActor;
import ir.haramyar.rozeh.domain.model.BookingId;
import ir.haramyar.rozeh.domain.model.BookingRuleViolation;
import ir.haramyar.rozeh.domain.model.BookingRuleViolation.Code;
import ir.haramyar.rozeh.domain.model.BookingStatus;
import ir.haramyar.rozeh.domain.model.CeremonyDetails;
import ir.haramyar.rozeh.domain.model.CeremonyLocation;
import ir.haramyar.rozeh.domain.model.CeremonyTime;
import ir.haramyar.rozeh.domain.model.HostContact;
import ir.haramyar.rozeh.domain.model.PhoneNumber;
import ir.haramyar.rozeh.domain.model.ProviderId;
import ir.haramyar.rozeh.domain.model.RozehBooking;
import ir.haramyar.rozeh.domain.model.TrackingCode;
import ir.haramyar.rozeh.domain.policy.BookingCancellationPolicy;
import ir.haramyar.rozeh.domain.policy.BookingPolicies;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RozehBookingTest {
  private final BookingPolicies policies = policies();

  private static void violates(Code code, ThrowingCallable action) {
    assertThatThrownBy(action).isInstanceOf(BookingRuleViolation.class).extracting(e -> ((BookingRuleViolation) e).code()).isEqualTo(code);
  }

  private RozehBooking accepted() {
    RozehBooking booking = requested();
    booking.accept(coordinator(), null, policies, NOW);
    booking.pullEvents();
    return booking;
  }

  private RozehBooking confirmed() {
    RozehBooking booking = accepted();
    booking.confirm(host(), policies, NOW);
    booking.pullEvents();
    return booking;
  }

  @Nested
  class Request {
    @Test
    void newBookingStartsRequestedWithHistoryAndEvent() {
      RozehBooking booking = RozehBooking.request(BookingId.newId(), new TrackingCode("RZ-AB12-CD34"), host(),
          new HostContact("  Host Name  ", new PhoneNumber("+989121234567")), new CeremonyLocation("Qom", null, "Street 12, No 4"),
          BookingFixtures.details(), inDays(3), policies, NOW);
      assertThat(booking.status()).isEqualTo(BookingStatus.REQUESTED);
      assertThat(booking.hostId().value()).isEqualTo(HOST);
      assertThat(booking.host().name()).isEqualTo("Host Name");
      assertThat(booking.providerId()).isEmpty();
      assertThat(booking.history()).singleElement().satisfies(entry -> {
        assertThat(entry.action()).isEqualTo(BookingAction.REQUEST);
        assertThat(entry.from()).isNull();
        assertThat(entry.to()).isEqualTo(BookingStatus.REQUESTED);
      });
      assertThat(booking.pullEvents()).singleElement().isInstanceOf(BookingEvent.BookingRequested.class);
      assertThat(booking.pullEvents()).as("events are pulled once").isEmpty();
    }

    @Test
    void ceremonyMustBeInTheFuture() {
      violates(Code.CEREMONY_TIME_NOT_IN_FUTURE, () -> requested(new CeremonyTime(NOW.minusSeconds(60), NOW.plusSeconds(3600), BookingFixtures.TEHRAN), policies));
      violates(Code.CEREMONY_TIME_NOT_IN_FUTURE, () -> requested(new CeremonyTime(NOW, NOW.plusSeconds(3600), BookingFixtures.TEHRAN), policies));
    }

    @Test
    void endMustBeAfterStartAndSlotBounded() {
      Instant start = NOW.plus(Duration.ofDays(2));
      violates(Code.CEREMONY_END_NOT_AFTER_START, () -> new CeremonyTime(start, start, BookingFixtures.TEHRAN));
      violates(Code.CEREMONY_END_NOT_AFTER_START, () -> new CeremonyTime(start, start.minusSeconds(1), BookingFixtures.TEHRAN));
      violates(Code.INVALID_VALUE, () -> new CeremonyTime(start, start.plus(Duration.ofHours(25)), BookingFixtures.TEHRAN));
    }

    @Test
    void requiredHostInformationIsValidated() {
      violates(Code.INVALID_VALUE, () -> new HostContact(" ", new PhoneNumber("+989121234567")));
      violates(Code.INVALID_VALUE, () -> new HostContact("A\u0000B", new PhoneNumber("+989121234567")));
      violates(Code.INVALID_VALUE, () -> new PhoneNumber("09121234567"));
      violates(Code.INVALID_VALUE, () -> new PhoneNumber("+98 912 123 4567"));
    }

    @Test
    void requiredLocationIsValidated() {
      violates(Code.INVALID_VALUE, () -> new CeremonyLocation("", "x", "Street 1"));
      violates(Code.INVALID_VALUE, () -> new CeremonyLocation("Tehran", null, "ab"));
      assertThat(new CeremonyLocation("Tehran", "  ", "Street 12").area()).isNull();
    }

    @Test
    void ceremonyDimensionsAreIndependentAndCheckedAgainstCatalog() {
      violates(Code.INVALID_VALUE, () -> new CeremonyDetails(null, "family", "in-person", null, null));
      violates(Code.INVALID_VALUE, () -> new CeremonyDetails("rozeh", "family", "in-person", 0, null));
      CeremonyDetails unknownAudience = new CeremonyDetails("rozeh", "children", "in-person", null, null);
      violates(Code.CEREMONY_DETAILS_NOT_ALLOWED, () -> RozehBooking.request(BookingId.newId(), new TrackingCode("RZ-AB12-CD34"), host(),
          new HostContact("Host", new PhoneNumber("+989121234567")), new CeremonyLocation("Qom", null, "Street 12"), unknownAudience, inDays(3), policies, NOW));
      CeremonyDetails noAudience = new CeremonyDetails("rozeh", null, "in-person", null, null);
      assertThat(RozehBooking.request(BookingId.newId(), new TrackingCode("RZ-AB12-CD34"), host(),
          new HostContact("Host", new PhoneNumber("+989121234567")), new CeremonyLocation("Qom", null, "Street 12"), noAudience, inDays(3), policies, NOW).details().audience()).isNull();
    }

    @Test
    void coordinatorsDoNotSubmitRequests() {
      violates(Code.ACTOR_NOT_PERMITTED, () -> RozehBooking.request(BookingId.newId(), new TrackingCode("RZ-AB12-CD34"), coordinator(),
          new HostContact("Host", new PhoneNumber("+989121234567")), new CeremonyLocation("Qom", null, "Street 12"), BookingFixtures.details(), inDays(3), policies, NOW));
    }
  }

  @Nested
  class AcceptAndReject {
    @Test
    void coordinatorAcceptsRequested() {
      RozehBooking booking = requested();
      booking.accept(coordinator(), "looks good", policies, NOW);
      assertThat(booking.status()).isEqualTo(BookingStatus.ACCEPTED);
      assertThat(booking.history()).last().satisfies(e -> {
        assertThat(e.from()).isEqualTo(BookingStatus.REQUESTED);
        assertThat(e.actorId()).isEqualTo(COORDINATOR);
        assertThat(e.detail()).isEqualTo("looks good");
      });
      assertThat(booking.pullEvents()).singleElement().isInstanceOf(BookingEvent.BookingAccepted.class);
    }

    @Test
    void coordinatorRejectsWithReason() {
      RozehBooking booking = requested();
      violates(Code.INVALID_VALUE, () -> booking.reject(coordinator(), "", policies, NOW));
      booking.reject(coordinator(), "No provider in this city", policies, NOW);
      assertThat(booking.status()).isEqualTo(BookingStatus.REJECTED);
      assertThat(booking.pullEvents()).singleElement().isInstanceOf(BookingEvent.BookingRejected.class);
    }

    @Test
    void hostsCannotAcceptOrRejectEvenTheirOwnBookings() {
      RozehBooking booking = requested();
      violates(Code.ACTOR_NOT_PERMITTED, () -> booking.accept(host(), null, policies, NOW));
      violates(Code.ACTOR_NOT_PERMITTED, () -> booking.reject(host(), "nope", policies, NOW));
      assertThat(booking.status()).isEqualTo(BookingStatus.REQUESTED);
    }

    @Test
    void onlyRequestedBookingsCanBeAcceptedOrRejectedAndRepeatsDoNotRewriteHistory() {
      RozehBooking booking = accepted();
      int entries = booking.history().size();
      violates(Code.INVALID_TRANSITION, () -> booking.accept(coordinator(), null, policies, NOW));
      violates(Code.INVALID_TRANSITION, () -> booking.reject(coordinator(), "changed my mind", policies, NOW));
      assertThat(booking.history()).hasSize(entries);
      assertThat(booking.pullEvents()).isEmpty();

      RozehBooking rejected = requested();
      rejected.reject(coordinator(), "Not available", policies, NOW);
      violates(Code.INVALID_TRANSITION, () -> rejected.reject(coordinator(), "Again", policies, NOW));
      violates(Code.INVALID_TRANSITION, () -> rejected.accept(coordinator(), null, policies, NOW));
    }
  }

  @Nested
  class Confirm {
    @Test
    void acceptedBookingIsConfirmedByThePolicyActor() {
      RozehBooking booking = accepted();
      booking.confirm(host(), policies, NOW);
      assertThat(booking.status()).isEqualTo(BookingStatus.CONFIRMED);
      assertThat(booking.pullEvents()).singleElement().isInstanceOf(BookingEvent.BookingConfirmed.class);
    }

    @Test
    void requestedBookingCannotSkipToConfirmed() {
      violates(Code.INVALID_TRANSITION, () -> requested().confirm(host(), policies, NOW));
    }

    @Test
    void rejectedOrCancelledBookingsCannotBeConfirmed() {
      RozehBooking rejected = requested();
      rejected.reject(coordinator(), "No capacity", policies, NOW);
      violates(Code.INVALID_TRANSITION, () -> rejected.confirm(host(), policies, NOW));
      RozehBooking cancelled = accepted();
      cancelled.cancel(host(), null, policies, NOW);
      violates(Code.INVALID_TRANSITION, () -> cancelled.confirm(host(), policies, NOW));
    }

    @Test
    void confirmingTwiceIsRejected() {
      violates(Code.INVALID_TRANSITION, () -> confirmed().confirm(host(), policies, NOW));
    }

    @Test
    void confirmationActorIsAConfigurablePolicy() {
      RozehBooking booking = accepted();
      violates(Code.ACTOR_NOT_PERMITTED, () -> booking.confirm(coordinator(), policies, NOW));
      BookingPolicies coordinatorConfirms = policies(EnumSet.of(BookingActor.Role.COORDINATOR), false, BookingCancellationPolicy.noDeadline());
      violates(Code.ACTOR_NOT_PERMITTED, () -> booking.confirm(host(), coordinatorConfirms, NOW));
      booking.confirm(coordinator(), coordinatorConfirms, NOW);
      assertThat(booking.status()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void anotherHostCannotConfirm() {
      violates(Code.NOT_BOOKING_OWNER, () -> accepted().confirm(BookingActor.host(OTHER_HOST), policies, NOW));
    }

    @Test
    void providerRequirementIsAConfigurablePolicy() {
      BookingPolicies requiresProvider = policies(EnumSet.of(BookingActor.Role.HOST), true, BookingCancellationPolicy.noDeadline());
      RozehBooking booking = accepted();
      violates(Code.PROVIDER_REQUIRED, () -> booking.confirm(host(), requiresProvider, NOW));
      booking.assignProvider(coordinator(), ProviderId.newId(), requiresProvider, NOW);
      booking.confirm(host(), requiresProvider, NOW);
      assertThat(booking.status()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void startedCeremonyCannotBeConfirmed() {
      RozehBooking booking = accepted();
      violates(Code.CEREMONY_ALREADY_STARTED, () -> booking.confirm(host(), policies, booking.time().start()));
    }
  }

  @Nested
  class ProviderAssignment {
    @Test
    void coordinatorAssignsAndReassignsBeforeConfirmation() {
      RozehBooking booking = accepted();
      ProviderId first = ProviderId.newId();
      ProviderId second = ProviderId.newId();
      booking.assignProvider(coordinator(), first, policies, NOW);
      booking.assignProvider(coordinator(), first, policies, NOW);
      booking.assignProvider(coordinator(), second, policies, NOW);
      assertThat(booking.providerId()).contains(second);
      assertThat(booking.history().stream().filter(e -> e.action() == BookingAction.ASSIGN_PROVIDER)).hasSize(2);
      assertThat(booking.pullEvents()).hasSize(2).allMatch(BookingEvent.ProviderAssigned.class::isInstance);
    }

    @Test
    void assignmentOnlyWhileAcceptedAndOnlyByCoordinators() {
      violates(Code.INVALID_TRANSITION, () -> requested().assignProvider(coordinator(), ProviderId.newId(), policies, NOW));
      violates(Code.INVALID_TRANSITION, () -> confirmed().assignProvider(coordinator(), ProviderId.newId(), policies, NOW));
      violates(Code.ACTOR_NOT_PERMITTED, () -> accepted().assignProvider(host(), ProviderId.newId(), policies, NOW));
    }
  }

  @Nested
  class Cancel {
    @Test
    void hostCancelsOwnBookingInAnyActiveState() {
      for (RozehBooking booking : new RozehBooking[] {requested(), accepted(), confirmed()}) {
        booking.cancel(host(), "Family emergency", policies, NOW);
        assertThat(booking.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(booking.pullEvents()).singleElement().isInstanceOf(BookingEvent.BookingCancelled.class);
      }
    }

    @Test
    void anotherHostCannotCancel() {
      violates(Code.NOT_BOOKING_OWNER, () -> requested().cancel(BookingActor.host(OTHER_HOST), null, policies, NOW));
    }

    @Test
    void coordinatorCancellationRequiresReason() {
      RozehBooking booking = accepted();
      violates(Code.INVALID_VALUE, () -> booking.cancel(coordinator(), null, policies, NOW));
      booking.cancel(coordinator(), "Provider unavailable", policies, NOW);
      assertThat(booking.status()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void terminalBookingsCannotBeCancelledOrRevived() {
      RozehBooking cancelled = requested();
      cancelled.cancel(host(), null, policies, NOW);
      violates(Code.INVALID_TRANSITION, () -> cancelled.cancel(host(), null, policies, NOW));
      violates(Code.INVALID_TRANSITION, () -> cancelled.accept(coordinator(), null, policies, NOW));
      RozehBooking rejected = requested();
      rejected.reject(coordinator(), "No provider", policies, NOW);
      violates(Code.INVALID_TRANSITION, () -> rejected.cancel(host(), null, policies, NOW));
    }

    @Test
    void noFixedDeadlineUnlessConfigured() {
      RozehBooking booking = confirmed();
      booking.cancel(host(), null, policies, booking.time().start().minusSeconds(1));
      assertThat(booking.status()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void configuredHostDeadlineIsEnforcedButNotForCoordinators() {
      BookingPolicies withDeadline = policies(EnumSet.of(BookingActor.Role.HOST), false, new BookingCancellationPolicy(Optional.of(Duration.ofHours(12))));
      RozehBooking booking = requested(inDays(1), withDeadline);
      Instant late = booking.time().start().minus(Duration.ofHours(2));
      violates(Code.CANCELLATION_DEADLINE_PASSED, () -> booking.cancel(host(), null, withDeadline, late));
      booking.cancel(coordinator(), "Provider fell ill", withDeadline, late);
      assertThat(booking.status()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void startedCeremonyCannotBeCancelled() {
      RozehBooking booking = confirmed();
      violates(Code.CEREMONY_ALREADY_STARTED, () -> booking.cancel(coordinator(), "too late", policies, booking.time().start()));
    }
  }

  @Nested
  class Complete {
    @Test
    void confirmedCeremonyIsCompletedOnceAfterItStarted() {
      RozehBooking booking = confirmed();
      violates(Code.CEREMONY_NOT_STARTED, () -> booking.complete(coordinator(), policies, NOW));
      Instant after = booking.time().end();
      booking.complete(coordinator(), policies, after);
      assertThat(booking.status()).isEqualTo(BookingStatus.COMPLETED);
      violates(Code.INVALID_TRANSITION, () -> booking.complete(coordinator(), policies, after));
      violates(Code.INVALID_TRANSITION, () -> booking.cancel(coordinator(), "after the fact", policies, after));
    }

    @Test
    void onlyConfirmedBookingsAreCompletedAndNotByHosts() {
      violates(Code.INVALID_TRANSITION, () -> accepted().complete(coordinator(), policies, NOW.plus(Duration.ofDays(30))));
      violates(Code.ACTOR_NOT_PERMITTED, () -> confirmed().complete(host(), policies, NOW.plus(Duration.ofDays(30))));
    }
  }

  @Test
  void everyTransitionIsAudited() {
    RozehBooking booking = requested();
    booking.accept(coordinator(), null, policies, NOW);
    booking.assignProvider(coordinator(), ProviderId.newId(), policies, NOW);
    booking.confirm(host(), policies, NOW);
    booking.complete(coordinator(), policies, booking.time().end());
    assertThat(booking.history()).extracting(e -> e.action()).containsExactly(
        BookingAction.REQUEST, BookingAction.ACCEPT, BookingAction.ASSIGN_PROVIDER, BookingAction.CONFIRM, BookingAction.COMPLETE);
    assertThat(booking.history()).extracting(e -> e.to()).containsExactly(
        BookingStatus.REQUESTED, BookingStatus.ACCEPTED, BookingStatus.ACCEPTED, BookingStatus.CONFIRMED, BookingStatus.COMPLETED);
  }

  @Test
  void stateMachineMatchesTheApprovedLifecycle() {
    assertThat(BookingStatus.REQUESTED.canTransitionTo(BookingStatus.CONFIRMED)).isFalse();
    assertThat(BookingStatus.REJECTED.canTransitionTo(BookingStatus.REQUESTED)).isFalse();
    assertThat(BookingStatus.CANCELLED.canTransitionTo(BookingStatus.REQUESTED)).isFalse();
    assertThat(EnumSet.allOf(BookingStatus.class).stream().filter(BookingStatus::isTerminal))
        .containsExactlyInAnyOrder(BookingStatus.REJECTED, BookingStatus.CANCELLED, BookingStatus.COMPLETED);
  }

  @Test
  void trackingCodesAndPhoneMaskingAreSafe() {
    violates(Code.INVALID_VALUE, () -> new TrackingCode("RZ-IIII-OOOO"));
    violates(Code.INVALID_VALUE, () -> new TrackingCode("rz-7k3m-q9td"));
    assertThat(new PhoneNumber("+989121234567").masked()).isEqualTo("+98912•••4567").doesNotContain("123");
  }
}
