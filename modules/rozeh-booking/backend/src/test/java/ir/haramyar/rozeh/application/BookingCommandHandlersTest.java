package ir.haramyar.rozeh.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ir.haramyar.rozeh.application.command.BookingCommands;
import ir.haramyar.rozeh.application.port.ConcurrentModification;
import ir.haramyar.rozeh.application.query.BookingView;
import ir.haramyar.rozeh.domain.BookingFixtures;
import ir.haramyar.rozeh.domain.model.BookingAction;
import ir.haramyar.rozeh.domain.model.BookingActor;
import ir.haramyar.rozeh.domain.model.BookingRuleViolation;
import ir.haramyar.rozeh.domain.model.BookingStatus;
import ir.haramyar.rozeh.domain.model.Provider;
import ir.haramyar.rozeh.domain.model.ProviderId;
import ir.haramyar.rozeh.domain.policy.BookingAvailabilityPolicy;
import ir.haramyar.rozeh.domain.policy.BookingPolicies;
import ir.haramyar.rozeh.infrastructure.adapter.LibPhoneNumbers;
import ir.haramyar.rozeh.infrastructure.adapter.SecureRandomTrackingCodes;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class BookingCommandHandlersTest {
  private final InMemoryAdapters.Bookings bookings = new InMemoryAdapters.Bookings();
  private final InMemoryAdapters.Providers providers = new InMemoryAdapters.Providers();
  private final InMemoryAdapters.Events events = new InMemoryAdapters.Events();
  private final Clock clock = Clock.fixed(BookingFixtures.NOW, ZoneOffset.UTC);
  private BookingCommandHandlers handlers;
  private final BookingActor host = BookingActor.host(BookingFixtures.HOST);
  private final BookingActor otherHost = BookingActor.host(BookingFixtures.OTHER_HOST);
  private final BookingActor coordinator = BookingActor.coordinator(BookingFixtures.COORDINATOR);

  private BookingCommandHandlers handlers(BookingPolicies policies, boolean requireVerifiedMobile) {
    PlatformTransactionManager tx = mock(PlatformTransactionManager.class);
    when(tx.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    return new BookingCommandHandlers(bookings, providers, events, new LibPhoneNumbers("IR", List.of("IR", "IQ")), new SecureRandomTrackingCodes(),
        new BookingAvailabilityPolicy(bookings), policies, new BookingAdmissionPolicy(requireVerifiedMobile), new BookingViews(providers, policies, clock),
        new TransactionTemplate(tx), clock);
  }

  @BeforeEach
  void setUp() { handlers = handlers(BookingFixtures.policies(), false); }

  private BookingCommands.RequestBooking request(BookingActor actor, UUID clientRequestId, String phone, boolean verified) {
    return new BookingCommands.RequestBooking(actor, verified, clientRequestId, "Fixture Host", phone, "Tehran", "Fixture area", "Fixture street 10",
        "rozeh", "family", "in-person", 25, null, BookingFixtures.NOW.plus(Duration.ofDays(5)), 60, BookingFixtures.TEHRAN);
  }

  @Test
  void requestPersistsNormalizedContactAndRecordsPrivacySafeEvent() {
    BookingView view = handlers.handle(request(host, UUID.randomUUID(), "۰۹۱۲ ۱۲۳ ۴۵۶۷", false));
    assertThat(view.status()).isEqualTo(BookingStatus.REQUESTED);
    assertThat(view.trackingCode()).matches("RZ-[0-9A-Z]{4}-[0-9A-Z]{4}");
    assertThat(view.host().phone()).isEqualTo("+989121234567");
    assertThat(view.allowedActions()).containsExactly(BookingAction.CANCEL);
    assertThat(events.recorded).singleElement().satisfies(event -> {
      assertThat(event.type()).isEqualTo("haramyar.booking.requested.v1");
      assertThat(event.payload()).containsKeys("bookingId", "trackingCode", "hostId", "ceremonyStartsAt", "occurredAt");
      assertThat(event.payload().toString()).doesNotContain("Fixture Host", "+98912", "Fixture street");
    });
  }

  @Test
  void duplicateSubmissionReturnsTheOriginalBooking() {
    UUID key = UUID.randomUUID();
    BookingView first = handlers.handle(request(host, key, "+989121234567", false));
    BookingView second = handlers.handle(request(host, key, "+989121234567", false));
    assertThat(second.id()).isEqualTo(first.id());
    assertThat(bookings.rows).hasSize(1);
    assertThat(events.recorded).hasSize(1);
  }

  @Test
  void invalidContactNumbersAreRejected() {
    for (String phone : new String[] {"12345", "+12025550123", "021 1234 5678", "abc"}) {
      assertThatThrownBy(() -> handlers.handle(request(host, UUID.randomUUID(), phone, false))).as(phone)
          .isInstanceOf(BookingRuleViolation.class).extracting(e -> ((BookingRuleViolation) e).code()).isEqualTo(BookingRuleViolation.Code.INVALID_VALUE);
    }
    assertThat(handlers.handle(request(host, UUID.randomUUID(), "+964 770 123 4567", false)).host().phone()).isEqualTo("+9647701234567");
    assertThat(handlers.handle(request(host, UUID.randomUUID(), "00989121234567", false)).host().phone()).isEqualTo("+989121234567");
  }

  @Test
  void verifiedMobileRequirementIsAnExplicitPolicy() {
    BookingCommandHandlers strict = handlers(BookingFixtures.policies(), true);
    assertThatThrownBy(() -> strict.handle(request(host, UUID.randomUUID(), "+989121234567", false)))
        .isInstanceOf(BookingRuleViolation.class).extracting(e -> ((BookingRuleViolation) e).code()).isEqualTo(BookingRuleViolation.Code.MOBILE_VERIFICATION_REQUIRED);
    assertThat(strict.handle(request(host, UUID.randomUUID(), "+989121234567", true)).status()).isEqualTo(BookingStatus.REQUESTED);
  }

  @Test
  void hostsCannotSeeOrChangeOtherHostsBookings() {
    UUID id = handlers.handle(request(host, UUID.randomUUID(), "+989121234567", false)).id();
    assertThatThrownBy(() -> handlers.handle(new BookingCommands.CancelBooking(otherHost, id, null, null))).isInstanceOf(BookingNotFound.class);
    assertThatThrownBy(() -> handlers.handle(new BookingCommands.ConfirmBooking(otherHost, id, null))).isInstanceOf(BookingNotFound.class);
    assertThat(bookings.updates).isZero();
  }

  @Test
  void fullLifecycleThroughHandlersPublishesIntegrationEvents() {
    UUID id = handlers.handle(request(host, UUID.randomUUID(), "+989121234567", false)).id();
    BookingView accepted = handlers.handle(new BookingCommands.AcceptBooking(coordinator, id, 0L, "ok"));
    assertThat(accepted.status()).isEqualTo(BookingStatus.ACCEPTED);
    assertThat(accepted.version()).isEqualTo(1);
    BookingView confirmed = handlers.handle(new BookingCommands.ConfirmBooking(host, id, 1L));
    assertThat(confirmed.status()).isEqualTo(BookingStatus.CONFIRMED);
    assertThat(events.recorded).extracting(e -> e.type()).containsExactly(
        "haramyar.booking.requested.v1", "haramyar.booking.accepted.v1", "haramyar.booking.confirmed.v1");
    assertThat(confirmed.history()).extracting(h -> h.action()).containsExactly(BookingAction.REQUEST, BookingAction.ACCEPT, BookingAction.CONFIRM);
  }

  @Test
  void staleExpectedVersionIsAConflict() {
    UUID id = handlers.handle(request(host, UUID.randomUUID(), "+989121234567", false)).id();
    handlers.handle(new BookingCommands.AcceptBooking(coordinator, id, 0L, null));
    assertThatThrownBy(() -> handlers.handle(new BookingCommands.CancelBooking(host, id, 0L, null))).isInstanceOf(ConcurrentModification.class);
  }

  @Test
  void inactiveProvidersCannotBeAssignedAndOverlapsAreRefused() {
    Provider active = Provider.register(ProviderId.newId(), "Provider One (fixture)", BookingFixtures.NOW);
    Provider inactive = Provider.register(ProviderId.newId(), "Provider Two (fixture)", BookingFixtures.NOW);
    inactive.deactivate();
    providers.add(active);
    providers.add(inactive);
    UUID first = handlers.handle(request(host, UUID.randomUUID(), "+989121234567", false)).id();
    handlers.handle(new BookingCommands.AcceptBooking(coordinator, first, null, null));
    assertThatThrownBy(() -> handlers.handle(new BookingCommands.AssignProvider(coordinator, first, null, inactive.id().value())))
        .isInstanceOf(BookingRuleViolation.class).extracting(e -> ((BookingRuleViolation) e).code()).isEqualTo(BookingRuleViolation.Code.PROVIDER_INACTIVE);
    handlers.handle(new BookingCommands.AssignProvider(coordinator, first, null, active.id().value()));
    assertThat(handlers.handle(new BookingCommands.ConfirmBooking(host, first, null)).provider().displayName()).isEqualTo("Provider One (fixture)");

    UUID second = handlers.handle(request(host, UUID.randomUUID(), "+989121234567", false)).id();
    handlers.handle(new BookingCommands.AcceptBooking(coordinator, second, null, null));
    assertThatThrownBy(() -> handlers.handle(new BookingCommands.AssignProvider(coordinator, second, null, active.id().value())))
        .isInstanceOf(BookingRuleViolation.class).extracting(e -> ((BookingRuleViolation) e).code()).isEqualTo(BookingRuleViolation.Code.PROVIDER_SCHEDULE_CONFLICT);
  }
}
