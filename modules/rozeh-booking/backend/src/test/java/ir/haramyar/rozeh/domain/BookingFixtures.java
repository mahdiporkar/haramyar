package ir.haramyar.rozeh.domain;

import ir.haramyar.rozeh.domain.model.BookingActor;
import ir.haramyar.rozeh.domain.model.BookingId;
import ir.haramyar.rozeh.domain.model.CeremonyDetails;
import ir.haramyar.rozeh.domain.model.CeremonyLocation;
import ir.haramyar.rozeh.domain.model.CeremonyTime;
import ir.haramyar.rozeh.domain.model.HostContact;
import ir.haramyar.rozeh.domain.model.PhoneNumber;
import ir.haramyar.rozeh.domain.model.RozehBooking;
import ir.haramyar.rozeh.domain.model.TrackingCode;
import ir.haramyar.rozeh.domain.policy.BookingActorPolicy;
import ir.haramyar.rozeh.domain.policy.BookingCancellationPolicy;
import ir.haramyar.rozeh.domain.policy.BookingConfirmationPolicy;
import ir.haramyar.rozeh.domain.policy.BookingPolicies;
import ir.haramyar.rozeh.domain.policy.CeremonyCatalog;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/** Test data. Names and numbers are synthetic development fixtures. */
public final class BookingFixtures {
  public static final Instant NOW = Instant.parse("2026-10-16T08:00:00Z");
  public static final ZoneId TEHRAN = ZoneId.of("Asia/Tehran");
  public static final UUID HOST = UUID.fromString("00000000-0000-0000-0000-00000000a001");
  public static final UUID OTHER_HOST = UUID.fromString("00000000-0000-0000-0000-00000000a002");
  public static final UUID COORDINATOR = UUID.fromString("00000000-0000-0000-0000-00000000c001");
  public static final CeremonyCatalog CATALOG = new CeremonyCatalog(Set.of("rozeh"), Set.of("family", "women", "men"), Set.of("in-person"));

  private BookingFixtures() {}

  public static BookingPolicies policies() {
    return policies(EnumSet.of(BookingActor.Role.HOST), false, BookingCancellationPolicy.noDeadline());
  }

  public static BookingPolicies policies(Set<BookingActor.Role> confirmers, boolean providerRequired, BookingCancellationPolicy cancellation) {
    return new BookingPolicies(BookingActorPolicy.provisional(confirmers), new BookingConfirmationPolicy(providerRequired), cancellation, CATALOG);
  }

  public static CeremonyTime inDays(int days) {
    Instant start = NOW.plus(Duration.ofDays(days));
    return new CeremonyTime(start, start.plus(Duration.ofMinutes(90)), TEHRAN);
  }

  public static CeremonyDetails details() {
    return new CeremonyDetails("rozeh", "family", "in-person", 30, null);
  }

  public static RozehBooking requested() {
    return requested(inDays(7), policies());
  }

  public static RozehBooking requested(CeremonyTime time, BookingPolicies policies) {
    RozehBooking booking = RozehBooking.request(BookingId.newId(), new TrackingCode("RZ-7K3M-Q9TD"), BookingActor.host(HOST),
        new HostContact("Test Host (fixture)", new PhoneNumber("+989120000001")),
        new CeremonyLocation("Tehran", "Fixture area", "Fixture street 1"), details(), time, policies, NOW);
    booking.pullEvents();
    return booking;
  }

  public static BookingActor host() { return BookingActor.host(HOST); }
  public static BookingActor coordinator() { return BookingActor.coordinator(COORDINATOR); }
}
