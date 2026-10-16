package ir.haramyar.shell.occasion;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Resolves the theme in effect: among scheduled occasions whose window contains {@code now}, the highest priority wins
 * (ties: the most recently started). Without an active occasion — or with invalid configuration — the default theme
 * applies.
 */
public record ActiveTheme(Occasion.Theme theme, Optional<Occasion> occasion) {
  public static ActiveTheme resolve(List<Occasion> occasions, Instant now) {
    return occasions.stream().filter(o -> o.activeAt(now))
        .max(Comparator.comparingInt(Occasion::priority).thenComparing(Occasion::startsAt))
        .map(o -> new ActiveTheme(o.theme(), Optional.of(o)))
        .orElse(new ActiveTheme(Occasion.Theme.DEFAULT, Optional.empty()));
  }
}
