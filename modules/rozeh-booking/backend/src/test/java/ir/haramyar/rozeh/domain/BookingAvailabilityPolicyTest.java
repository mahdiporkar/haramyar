package ir.haramyar.rozeh.domain;

import static ir.haramyar.rozeh.domain.BookingFixtures.NOW;
import static ir.haramyar.rozeh.domain.BookingFixtures.coordinator;
import static ir.haramyar.rozeh.domain.BookingFixtures.policies;
import static ir.haramyar.rozeh.domain.BookingFixtures.requested;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ir.haramyar.rozeh.domain.model.BookingRuleViolation;
import ir.haramyar.rozeh.domain.model.CeremonyTime;
import ir.haramyar.rozeh.domain.model.ProviderId;
import ir.haramyar.rozeh.domain.model.RozehBooking;
import ir.haramyar.rozeh.domain.policy.BookingAvailabilityPolicy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BookingAvailabilityPolicyTest {
  @Test
  void overlappingConfirmedProviderSlotsAreRejected() {
    ProviderId provider = ProviderId.newId();
    List<RozehBooking> confirmed = new ArrayList<>();
    BookingAvailabilityPolicy policy = new BookingAvailabilityPolicy((p, time, excluding) ->
        confirmed.stream().anyMatch(b -> b.providerId().orElseThrow().equals(p) && !b.id().equals(excluding) && b.time().overlaps(time)));

    RozehBooking first = requested();
    first.accept(coordinator(), null, policies(), NOW);
    first.assignProvider(coordinator(), provider, policies(), NOW);
    confirmed.add(first);

    CeremonyTime overlapping = new CeremonyTime(first.time().start().plus(Duration.ofMinutes(30)), first.time().end().plus(Duration.ofMinutes(30)), first.time().zone());
    RozehBooking second = requested(overlapping, policies());
    second.accept(coordinator(), null, policies(), NOW);
    second.assignProvider(coordinator(), provider, policies(), NOW);
    assertThatThrownBy(() -> policy.ensureProviderAvailable(second)).isInstanceOf(BookingRuleViolation.class)
        .extracting(e -> ((BookingRuleViolation) e).code()).isEqualTo(BookingRuleViolation.Code.PROVIDER_SCHEDULE_CONFLICT);

    CeremonyTime adjacent = new CeremonyTime(first.time().end(), first.time().end().plus(Duration.ofMinutes(60)), first.time().zone());
    RozehBooking third = requested(adjacent, policies());
    third.accept(coordinator(), null, policies(), NOW);
    third.assignProvider(coordinator(), provider, policies(), NOW);
    assertThatCode(() -> policy.ensureProviderAvailable(third)).as("back-to-back slots do not overlap").doesNotThrowAnyException();
    assertThatCode(() -> policy.ensureProviderAvailable(first)).as("a booking never conflicts with itself").doesNotThrowAnyException();
  }

  @Test
  void bookingsWithoutProviderAreAlwaysAvailable() {
    BookingAvailabilityPolicy policy = new BookingAvailabilityPolicy((p, time, excluding) -> true);
    assertThatCode(() -> policy.ensureProviderAvailable(requested())).doesNotThrowAnyException();
  }
}
