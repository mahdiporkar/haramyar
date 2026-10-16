package ir.haramyar.martyrs.application;

import java.util.Locale;

/**
 * Normalizes Persian/Arabic text for search so equivalent spellings match: Arabic Yeh/Alef Maksura to Persian Yeh,
 * Arabic Kaf to Keheh, diacritics and tatweel removed, Persian and Arabic-Indic digits to ASCII, ZWNJ and whitespace
 * collapsed.
 */
public final class SearchText {
  private SearchText() {}

  public static String normalize(String value) {
    if (value == null) return "";
    var out = new StringBuilder(value.length());
    for (char c : value.toCharArray()) {
      if (c == 'ي' || c == 'ى') out.append('ی');
      else if (c == 'ك') out.append('ک');
      else if ((c >= 'ً' && c <= 'ٟ') || c == 'ٰ' || c == 'ـ') continue;
      else if (c >= '۰' && c <= '۹') out.append((char) ('0' + c - '۰'));
      else if (c >= '٠' && c <= '٩') out.append((char) ('0' + c - '٠'));
      else if (c == '‌' || Character.isWhitespace(c)) out.append(' ');
      else out.append(c);
    }
    return out.toString().toLowerCase(Locale.ROOT).replaceAll(" +", " ").strip();
  }
}
