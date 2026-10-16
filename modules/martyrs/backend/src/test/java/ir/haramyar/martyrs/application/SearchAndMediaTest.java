package ir.haramyar.martyrs.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class SearchAndMediaTest {
  @Test
  void persianAndArabicSpellingsNormalizeToTheSameSearchText() {
    assertThat(SearchText.normalize("علي")).isEqualTo(SearchText.normalize("علی"));
    assertThat(SearchText.normalize("كاظم")).isEqualTo(SearchText.normalize("کاظم"));
    assertThat(SearchText.normalize("مُحَمَّد")).isEqualTo("محمد");
    assertThat(SearchText.normalize("  سال ۱۳۶۵‌ها  ")).isEqualTo("سال 1365 ها");
    assertThat(SearchText.normalize(null)).isEmpty();
  }

  @Test
  void imagesAreRecognizedByContentNotByName() {
    byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0, 0, 0, 0, 0};
    byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    byte[] webp = "RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1);
    byte[] svg = "<svg xmlns='http://www.w3.org/2000/svg' onload='alert(1)'/>".getBytes(StandardCharsets.UTF_8);
    byte[] html = "<!doctype html><script>alert(1)</script>".getBytes(StandardCharsets.UTF_8);
    assertThat(ImageInspector.detect(jpeg)).contains(ImageInspector.ImageType.JPEG);
    assertThat(ImageInspector.detect(png)).contains(ImageInspector.ImageType.PNG);
    assertThat(ImageInspector.detect(webp)).contains(ImageInspector.ImageType.WEBP);
    assertThat(ImageInspector.detect(svg)).isEmpty();
    assertThat(ImageInspector.detect(html)).isEmpty();
    assertThat(ImageInspector.detect(new byte[3])).isEmpty();
  }
}
