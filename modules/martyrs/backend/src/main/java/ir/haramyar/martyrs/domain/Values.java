package ir.haramyar.martyrs.domain;

import java.net.URI;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Value objects of the Martyrs context. */
public final class Values {
  private Values() {}

  public record MartyrId(UUID value) {
    public MartyrId { Objects.requireNonNull(value, "martyrId"); }
    public static MartyrId newId() { return new MartyrId(UUID.randomUUID()); }
    @Override public String toString() { return value.toString(); }
  }

  /** Stable public URL segment; ASCII so canonical URLs stay readable and unambiguous across scripts. */
  public record MartyrSlug(String value) {
    private static final Pattern FORMAT = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    public MartyrSlug {
      if (value == null || value.length() < 3 || value.length() > 120 || !FORMAT.matcher(value).matches())
        throw EditorialRuleViolation.invalid("Slug must be 3-120 lowercase letters, digits and single hyphens");
    }
    @Override public String toString() { return value; }
  }

  public record MartyrName(String fullName, String honorific) {
    public MartyrName {
      fullName = Text.required(fullName, "Full name", 2, 160);
      honorific = Text.optional(honorific, "Honorific", 80);
    }
  }

  /** Long-form biography as plain paragraphs (blank-line separated); never rendered as HTML. */
  public record Biography(String text) {
    public static final int MAX = 50_000;
    public Biography { text = Text.optional(text, "Biography", MAX); }
    public boolean isEmpty() { return text == null; }
  }

  /**
   * A possibly partial historical date. Calendars are recorded as entered because historical sources use Solar Hijri,
   * Lunar Hijri or Gregorian dates; no conversion is invented.
   */
  public record HistoricalDate(Integer year, Integer month, Integer day, Calendar calendar) {
    public enum Calendar { SOLAR_HIJRI, LUNAR_HIJRI, GREGORIAN }
    public HistoricalDate {
      Objects.requireNonNull(calendar, "calendar");
      if (year == null || year < 1 || year > 3000) throw EditorialRuleViolation.invalid("Year is required and must be plausible");
      if (month != null && (month < 1 || month > 12)) throw EditorialRuleViolation.invalid("Month must be 1-12");
      if (day != null && (month == null || day < 1 || day > 31)) throw EditorialRuleViolation.invalid("Day requires a month and must be 1-31");
    }
  }

  public record LifeEvent(HistoricalDate date, String place) {
    public LifeEvent { place = Text.optional(place, "Place", 160); }
    public boolean isEmpty() { return date == null && place == null; }
  }

  /** Reference to a stored, validated media object. */
  public record MediaReference(UUID mediaId, String caption, String credit) {
    public MediaReference {
      Objects.requireNonNull(mediaId, "mediaId");
      caption = Text.optional(caption, "Caption", 300);
      credit = Text.optional(credit, "Credit", 200);
    }
  }

  /** Where a statement comes from. Publication requires at least one. */
  public record SourceReference(UUID id, Kind kind, String title, String author, String publisher, Integer year, URI url, String note) {
    public enum Kind { BOOK, ARCHIVE, INTERVIEW, ARTICLE, WEBSITE, OTHER }
    public SourceReference {
      Objects.requireNonNull(id, "id");
      Objects.requireNonNull(kind, "kind");
      title = Text.required(title, "Source title", 2, 300);
      author = Text.optional(author, "Author", 200);
      publisher = Text.optional(publisher, "Publisher", 200);
      note = Text.optional(note, "Note", 1000);
      if (year != null && (year < 1 || year > 3000)) throw EditorialRuleViolation.invalid("Source year is implausible");
      if (url != null && (!Set.of("https", "http").contains(String.valueOf(url.getScheme()).toLowerCase()) || url.getHost() == null || url.getUserInfo() != null))
        throw EditorialRuleViolation.invalid("Source URL must be an http(s) address without credentials");
    }
  }

  /** A narrative attached to a profile, with the sources it relies on. */
  public record Story(UUID id, String title, String body, Set<UUID> sourceIds) {
    public Story {
      Objects.requireNonNull(id, "id");
      title = Text.required(title, "Story title", 2, 200);
      body = Text.required(body, "Story body", 20, 20_000);
      sourceIds = Set.copyOf(sourceIds == null ? Set.of() : sourceIds);
    }
  }

  public record SeoMetadata(String title, String description) {
    public SeoMetadata {
      title = Text.optional(title, "SEO title", 70);
      description = Text.optional(description, "SEO description", 170);
    }
  }

  public enum PublicationStatus { DRAFT, IN_REVIEW, PUBLISHED, ARCHIVED }

  /** Capacity in which a staff member acts; established by the interface layer from the secured request. */
  public record EditorialActor(UUID userId, Role role) {
    public enum Role { EDITOR, REVIEWER }
    public EditorialActor {
      Objects.requireNonNull(userId, "userId");
      Objects.requireNonNull(role, "role");
    }
  }
}
