package ir.haramyar.martyrs.domain;

/** Guards for editorial text: trimmed, bounded, no control characters other than line breaks. Markup is never interpreted. */
final class Text {
  private Text() {}

  static String required(String value, String field, int min, int max) {
    String trimmed = value == null ? "" : value.strip();
    if (trimmed.length() < min || trimmed.length() > max) throw EditorialRuleViolation.invalid(field + " must contain " + min + " to " + max + " characters");
    for (int i = 0; i < trimmed.length(); i++) {
      char c = trimmed.charAt(i);
      if (Character.isISOControl(c) && c != '\n' && c != '\r') throw EditorialRuleViolation.invalid(field + " contains control characters");
    }
    return trimmed.replace("\r\n", "\n");
  }

  static String optional(String value, String field, int max) {
    return value == null || value.isBlank() ? null : required(value, field, 1, max);
  }
}
