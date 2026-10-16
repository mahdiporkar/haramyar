package ir.haramyar.rozeh.domain.model;

import java.util.regex.Pattern;

/**
 * A contact number in canonical E.164 form. Parsing user input into this form (national formats, Persian and Arabic
 * digits) is an application concern; the domain only accepts the canonical representation, so two spellings of one
 * number can never become two different values.
 */
public record PhoneNumber(String e164) {
  private static final Pattern E164 = Pattern.compile("\\+[1-9][0-9]{7,14}");

  public PhoneNumber {
    if (e164 == null || !E164.matcher(e164).matches()) throw BookingRuleViolation.invalid("Contact number must be in international E.164 form");
  }

  /** Masked form for lists and logs, e.g. {@code +98912•••4567}. */
  public String masked() {
    return e164.substring(0, Math.min(6, e164.length() - 4)) + "•••" + e164.substring(e164.length() - 4);
  }

  @Override public String toString() { return masked(); }
}
