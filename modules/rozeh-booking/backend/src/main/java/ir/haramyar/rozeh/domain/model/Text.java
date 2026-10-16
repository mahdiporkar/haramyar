package ir.haramyar.rozeh.domain.model;

/** Shared guards for free-text value objects: trimmed, bounded, no control characters. */
final class Text {
  private Text() {}

  static String required(String value, String field, int min, int max) {
    String trimmed = value == null ? "" : value.strip();
    if (trimmed.length() < min || trimmed.length() > max) throw BookingRuleViolation.invalid(field + " must contain " + min + " to " + max + " characters");
    rejectControl(trimmed, field);
    return trimmed;
  }

  static String optional(String value, String field, int max) {
    if (value == null || value.isBlank()) return null;
    return required(value, field, 1, max);
  }

  private static void rejectControl(String value, String field) {
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (Character.isISOControl(c) && c != '\n') throw BookingRuleViolation.invalid(field + " contains control characters");
    }
  }
}
