package ir.haramyar.keycloak;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import java.util.Optional;
import java.util.Set;

/**
 * Canonical form of a HaramYar username: an Iranian (+98) or Iraqi (+964) mobile number in E.164. Accepts national
 * input ({@code 0912...} is read as Iranian), international input with {@code +} or {@code 00}, spaces, dashes,
 * parentheses and Persian or Arabic-Indic digits.
 */
public final class MobileNumbers {
  static final Set<String> REGIONS = Set.of("IR", "IQ");
  private static final PhoneNumberUtil UTIL = PhoneNumberUtil.getInstance();

  private MobileNumbers() {}

  public static Optional<String> normalize(String raw) {
    if (raw == null) return Optional.empty();
    String value = asciiDigits(raw.strip());
    if (value.isEmpty() || value.length() > 32 || value.contains("@")) return Optional.empty();
    if (value.startsWith("00")) value = "+" + value.substring(2);
    try {
      var number = UTIL.parse(value, "IR");
      String region = UTIL.getRegionCodeForNumber(number);
      var type = UTIL.getNumberType(number);
      boolean mobile = type == PhoneNumberUtil.PhoneNumberType.MOBILE || type == PhoneNumberUtil.PhoneNumberType.FIXED_LINE_OR_MOBILE;
      if (!UTIL.isValidNumber(number) || region == null || !REGIONS.contains(region) || !mobile) return Optional.empty();
      return Optional.of(UTIL.format(number, PhoneNumberUtil.PhoneNumberFormat.E164));
    } catch (NumberParseException invalid) {
      return Optional.empty();
    }
  }

  /** Heuristic: the input is meant as a phone number (digits and phone punctuation only), not an e-mail or name. */
  public static boolean looksLikePhone(String raw) {
    return raw != null && asciiDigits(raw.strip()).matches("[+0-9 ()\\-]{6,32}");
  }

  static String asciiDigits(String value) {
    var out = new StringBuilder(value.length());
    for (char c : value.toCharArray()) {
      if (c >= '۰' && c <= '۹') out.append((char) ('0' + (c - '۰')));
      else if (c >= '٠' && c <= '٩') out.append((char) ('0' + (c - '٠')));
      else out.append(c);
    }
    return out.toString();
  }
}
