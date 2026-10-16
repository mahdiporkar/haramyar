package ir.haramyar.shell.occasion;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * An occasion that switches the visual theme for an explicit time window. Lunar-calendar dates are never computed
 * here: administrators enter the window explicitly (validated, Gregorian instants). Themes are presentation only and
 * never change business state.
 */
public final class Occasion {
  public enum Theme { DEFAULT, CELEBRATION, MOURNING }
  public enum Status { DRAFT, SCHEDULED, ARCHIVED }

  public static final Set<String> LOCALES = Set.of("fa", "ar", "en");
  private static final Pattern KEY = Pattern.compile("[a-z][a-z0-9-]{2,59}");

  private final UUID id;
  private final String key;
  private Theme theme;
  private Map<String, String> titles;
  private Map<String, String> subtitles;
  private Instant startsAt;
  private Instant endsAt;
  private int priority;
  private Status status;
  private final long version;

  private Occasion(UUID id, String key, Theme theme, Map<String, String> titles, Map<String, String> subtitles, Instant startsAt, Instant endsAt,
      int priority, Status status, long version) {
    this.id = Objects.requireNonNull(id);
    if (key == null || !KEY.matcher(key).matches()) throw new IllegalArgumentException("Occasion key must be 3-60 lowercase letters, digits or hyphens");
    this.key = key;
    this.status = Objects.requireNonNull(status);
    this.version = version;
    define(theme, titles, subtitles, startsAt, endsAt, priority);
  }

  public static Occasion draft(String key, Theme theme, Map<String, String> titles, Map<String, String> subtitles, Instant startsAt, Instant endsAt, int priority) {
    return new Occasion(UUID.randomUUID(), key, theme, titles, subtitles, startsAt, endsAt, priority, Status.DRAFT, 0);
  }

  public static Occasion reconstitute(UUID id, String key, Theme theme, Map<String, String> titles, Map<String, String> subtitles, Instant startsAt, Instant endsAt,
      int priority, Status status, long version) {
    return new Occasion(id, key, theme, titles, subtitles, startsAt, endsAt, priority, status, version);
  }

  public void revise(Theme newTheme, Map<String, String> newTitles, Map<String, String> newSubtitles, Instant newStart, Instant newEnd, int newPriority) {
    if (status == Status.ARCHIVED) throw new IllegalStateException("Archived occasions cannot change");
    define(newTheme, newTitles, newSubtitles, newStart, newEnd, newPriority);
  }

  public void schedule() {
    if (status != Status.DRAFT) throw new IllegalStateException("Only drafts can be scheduled");
    status = Status.SCHEDULED;
  }

  public void archive() {
    if (status == Status.ARCHIVED) throw new IllegalStateException("Already archived");
    status = Status.ARCHIVED;
  }

  public boolean activeAt(Instant now) {
    return status == Status.SCHEDULED && !now.isBefore(startsAt) && now.isBefore(endsAt);
  }

  private void define(Theme newTheme, Map<String, String> newTitles, Map<String, String> newSubtitles, Instant newStart, Instant newEnd, int newPriority) {
    Objects.requireNonNull(newTheme, "theme");
    Objects.requireNonNull(newStart, "startsAt");
    Objects.requireNonNull(newEnd, "endsAt");
    if (!newEnd.isAfter(newStart)) throw new IllegalArgumentException("The occasion must end after it starts");
    if (newPriority < 0 || newPriority > 1000) throw new IllegalArgumentException("Priority must be 0-1000");
    theme = newTheme;
    titles = localized(newTitles, "title", 120, true);
    subtitles = localized(newSubtitles, "subtitle", 300, false);
    startsAt = newStart;
    endsAt = newEnd;
    priority = newPriority;
  }

  private static Map<String, String> localized(Map<String, String> values, String field, int max, boolean faRequired) {
    Map<String, String> source = values == null ? Map.of() : values;
    if (!LOCALES.containsAll(source.keySet())) throw new IllegalArgumentException("Only fa, ar and en " + field + "s are supported");
    if (faRequired && (source.get("fa") == null || source.get("fa").isBlank())) throw new IllegalArgumentException("A Persian " + field + " is required");
    var clean = new java.util.TreeMap<String, String>();
    source.forEach((locale, text) -> {
      if (text == null || text.isBlank()) return;
      String trimmed = text.strip();
      if (trimmed.length() > max || trimmed.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("Invalid " + field);
      clean.put(locale, trimmed);
    });
    return Map.copyOf(clean);
  }

  public UUID id() { return id; }
  public String key() { return key; }
  public Theme theme() { return theme; }
  public Map<String, String> titles() { return titles; }
  public Map<String, String> subtitles() { return subtitles; }
  public Instant startsAt() { return startsAt; }
  public Instant endsAt() { return endsAt; }
  public int priority() { return priority; }
  public Status status() { return status; }
  public long version() { return version; }
}
