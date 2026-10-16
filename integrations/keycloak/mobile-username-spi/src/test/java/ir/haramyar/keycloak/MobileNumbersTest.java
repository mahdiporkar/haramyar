package ir.haramyar.keycloak;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MobileNumbersTest {
  @Test
  void everySpellingOfOneIranianNumberHasOneCanonicalForm() {
    for (String input : new String[] {"09121234567", "9121234567", "+989121234567", "00989121234567", "+98 912 123 4567", "0912-123-4567",
        "(0912) 123 4567", "۰۹۱۲۱۲۳۴۵۶۷", "٠٩١٢١٢٣٤٥٦٧", " +۹۸۹۱۲۱۲۳۴۵۶۷ "}) {
      assertThat(MobileNumbers.normalize(input)).as(input).contains("+989121234567");
    }
  }

  @Test
  void iraqiNumbersRequireTheirCountryCode() {
    assertThat(MobileNumbers.normalize("+964 770 123 4567")).contains("+9647701234567");
    assertThat(MobileNumbers.normalize("009647701234567")).contains("+9647701234567");
  }

  @Test
  void nonMobileForeignAndMalformedInputIsRejected() {
    for (String input : new String[] {null, "", "02112345678", "+12025550123", "+447700900123", "12345", "0912123456", "abc", "user@example.org", "+98912123456789012"}) {
      assertThat(MobileNumbers.normalize(input)).as(String.valueOf(input)).isEmpty();
    }
  }

  @Test
  void onlyPhoneShapedInputIsTouchedAtLogin() {
    assertThat(MobileNumbers.looksLikePhone("0912 123 4567")).isTrue();
    assertThat(MobileNumbers.looksLikePhone("۰۹۱۲۱۲۳۴۵۶۷")).isTrue();
    assertThat(MobileNumbers.looksLikePhone("staff.admin")).isFalse();
    assertThat(MobileNumbers.looksLikePhone("someone@example.org")).isFalse();
  }
}
