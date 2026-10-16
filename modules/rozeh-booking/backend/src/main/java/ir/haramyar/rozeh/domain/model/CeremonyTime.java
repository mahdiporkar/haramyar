package ir.haramyar.rozeh.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;

/**
 * The proposed ceremony slot. Instants are absolute (UTC); {@code zone} is the ceremony's local time zone, kept for
 * presentation (for example Jalali dates in {@code Asia/Tehran}).
 */
public record CeremonyTime(Instant start, Instant end, ZoneId zone) {
  /** Technical sanity bound, not a business rule: a single home ceremony slot cannot exceed one day. */
  public static final Duration MAX_LENGTH = Duration.ofHours(24);

  public CeremonyTime {
    Objects.requireNonNull(start, "start");
    Objects.requireNonNull(end, "end");
    Objects.requireNonNull(zone, "zone");
    if (!end.isAfter(start)) throw new BookingRuleViolation(BookingRuleViolation.Code.CEREMONY_END_NOT_AFTER_START, "Ceremony end must be after its start");
    if (Duration.between(start, end).compareTo(MAX_LENGTH) > 0) throw BookingRuleViolation.invalid("A ceremony slot cannot exceed 24 hours");
  }

  public boolean overlaps(CeremonyTime other) {
    return start.isBefore(other.end) && other.start.isBefore(end);
  }

  public boolean hasStartedAt(Instant now) {
    return !now.isBefore(start);
  }
}
