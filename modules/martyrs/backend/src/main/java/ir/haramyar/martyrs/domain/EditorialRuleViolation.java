package ir.haramyar.martyrs.domain;

/** A rule of the Martyrs editorial context was violated; the code is part of the API contract. */
public final class EditorialRuleViolation extends RuntimeException {
  public enum Code { INVALID_VALUE, INVALID_TRANSITION, ACTOR_NOT_PERMITTED, NOT_EDITABLE, NOT_READY_FOR_REVIEW, FOUR_EYES_REQUIRED, LIMIT_EXCEEDED, NOT_FOUND }

  private final Code code;

  public EditorialRuleViolation(Code code, String message) {
    super(message);
    this.code = code;
  }

  public Code code() { return code; }

  public static EditorialRuleViolation invalid(String message) { return new EditorialRuleViolation(Code.INVALID_VALUE, message); }
}
