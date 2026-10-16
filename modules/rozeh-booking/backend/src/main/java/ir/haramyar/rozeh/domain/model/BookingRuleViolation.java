package ir.haramyar.rozeh.domain.model;

/** A business rule of the Rozeh Booking context was violated. The code is part of the public API contract. */
public final class BookingRuleViolation extends RuntimeException {
  public enum Code {
    INVALID_VALUE,
    CEREMONY_TIME_NOT_IN_FUTURE,
    CEREMONY_END_NOT_AFTER_START,
    CEREMONY_ALREADY_STARTED,
    CEREMONY_NOT_STARTED,
    CEREMONY_DETAILS_NOT_ALLOWED,
    INVALID_TRANSITION,
    ACTOR_NOT_PERMITTED,
    NOT_BOOKING_OWNER,
    PROVIDER_REQUIRED,
    PROVIDER_INACTIVE,
    PROVIDER_SCHEDULE_CONFLICT,
    CANCELLATION_DEADLINE_PASSED,
    MOBILE_VERIFICATION_REQUIRED
  }

  private final Code code;

  public BookingRuleViolation(Code code, String message) {
    super(message);
    this.code = code;
  }

  public Code code() { return code; }

  static BookingRuleViolation invalid(String message) { return new BookingRuleViolation(Code.INVALID_VALUE, message); }
}
