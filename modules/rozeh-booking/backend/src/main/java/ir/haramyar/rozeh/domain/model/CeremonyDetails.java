package ir.haramyar.rozeh.domain.model;

import java.util.regex.Pattern;

/**
 * Independent business dimensions of the ceremony. Category, intended audience and delivery mode are separate codes
 * (never one combined enum); which codes are allowed is decided by the {@link ir.haramyar.rozeh.domain.policy.CeremonyCatalog}
 * because the final lists are not yet approved by the business.
 *
 * @param category ceremony type code, e.g. {@code rozeh}
 * @param audience intended audience code, optional, e.g. {@code family}
 * @param deliveryMode how the ceremony is delivered, e.g. {@code in-person}
 * @param approximateCapacity optional expected number of guests
 * @param notes optional free-text notes from the host
 */
public record CeremonyDetails(String category, String audience, String deliveryMode, Integer approximateCapacity, String notes) {
  private static final Pattern CODE = Pattern.compile("[a-z][a-z0-9-]{1,39}");

  public CeremonyDetails {
    category = code(category, "Ceremony category", true);
    audience = code(audience, "Audience", false);
    deliveryMode = code(deliveryMode, "Delivery mode", true);
    if (approximateCapacity != null && (approximateCapacity < 1 || approximateCapacity > 5000)) throw BookingRuleViolation.invalid("Approximate capacity must be between 1 and 5000");
    notes = Text.optional(notes, "Notes", 1000);
  }

  private static String code(String value, String field, boolean required) {
    if (value == null || value.isBlank()) {
      if (required) throw BookingRuleViolation.invalid(field + " is required");
      return null;
    }
    if (!CODE.matcher(value).matches()) throw BookingRuleViolation.invalid(field + " code is malformed");
    return value;
  }
}
