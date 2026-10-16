package ir.haramyar.rozeh.domain.model;

import java.util.regex.Pattern;

/**
 * Human-friendly, unguessable reference shown to hosts after submission ({@code RZ-XXXX-XXXX}, Crockford base32
 * without ambiguous characters). It identifies a request in conversation; it never authorizes access to it.
 */
public record TrackingCode(String value) {
  public static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
  private static final Pattern FORMAT = Pattern.compile("RZ-[0-9A-HJKMNP-TV-Z]{4}-[0-9A-HJKMNP-TV-Z]{4}");

  public TrackingCode {
    if (value == null || !FORMAT.matcher(value).matches()) throw BookingRuleViolation.invalid("Malformed tracking code");
  }

  @Override public String toString() { return value; }
}
