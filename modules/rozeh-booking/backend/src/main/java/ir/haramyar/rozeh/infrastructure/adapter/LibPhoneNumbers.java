package ir.haramyar.rozeh.infrastructure.adapter;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import ir.haramyar.rozeh.application.port.PhoneNumbers;
import ir.haramyar.rozeh.domain.model.PhoneNumber;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * libphonenumber-based normalization. Persian (U+06F0..U+06F9) and Arabic-Indic (U+0660..U+0669) digits are mapped
 * to ASCII; national input is interpreted in the configured default region; only mobile numbers of the supported
 * regions (initially Iran and Iraq) are accepted.
 */
public class LibPhoneNumbers implements PhoneNumbers {
  private final PhoneNumberUtil util = PhoneNumberUtil.getInstance();
  private final String defaultRegion;
  private final Set<String> allowedRegions;

  public LibPhoneNumbers(String defaultRegion, List<String> allowedRegions) {
    this.defaultRegion = defaultRegion;
    this.allowedRegions = Set.copyOf(allowedRegions);
  }

  @Override
  public Optional<PhoneNumber> normalize(String raw) {
    if (raw == null || raw.isBlank() || raw.length() > 40) return Optional.empty();
    String ascii = asciiDigits(raw.strip());
    if (ascii.startsWith("00")) ascii = "+" + ascii.substring(2);
    try {
      var parsed = util.parse(ascii, defaultRegion);
      String region = util.getRegionCodeForNumber(parsed);
      var type = util.getNumberType(parsed);
      boolean mobile = type == PhoneNumberUtil.PhoneNumberType.MOBILE || type == PhoneNumberUtil.PhoneNumberType.FIXED_LINE_OR_MOBILE;
      if (!util.isValidNumber(parsed) || region == null || !allowedRegions.contains(region) || !mobile) return Optional.empty();
      return Optional.of(new PhoneNumber(util.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164)));
    } catch (NumberParseException invalid) {
      return Optional.empty();
    }
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
