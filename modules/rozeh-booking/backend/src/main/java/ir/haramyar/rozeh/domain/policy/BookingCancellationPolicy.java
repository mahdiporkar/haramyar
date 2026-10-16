package ir.haramyar.rozeh.domain.policy;

import ir.haramyar.rozeh.domain.model.BookingActor;
import ir.haramyar.rozeh.domain.model.CeremonyTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * When a host may still cancel. The cancellation deadline is not yet approved, so no fixed notice period is assumed:
 * {@code hostMinimumNotice} is empty unless configured. Independently of configuration, nobody can cancel a ceremony
 * that has already started (it can only be completed).
 */
public record BookingCancellationPolicy(Optional<Duration> hostMinimumNotice) {
  public BookingCancellationPolicy {
    hostMinimumNotice = hostMinimumNotice == null ? Optional.empty() : hostMinimumNotice;
    hostMinimumNotice.ifPresent(notice -> { if (notice.isNegative()) throw new IllegalArgumentException("Notice cannot be negative"); });
  }

  public static BookingCancellationPolicy noDeadline() { return new BookingCancellationPolicy(Optional.empty()); }

  public boolean deadlinePassed(BookingActor.Role role, CeremonyTime time, Instant now) {
    if (role != BookingActor.Role.HOST) return false;
    return hostMinimumNotice.map(notice -> !now.isBefore(time.start().minus(notice))).orElse(false);
  }
}
