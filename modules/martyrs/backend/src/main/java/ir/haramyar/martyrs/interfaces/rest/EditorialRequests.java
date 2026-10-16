package ir.haramyar.martyrs.interfaces.rest;

import ir.haramyar.martyrs.domain.MartyrProfile;
import ir.haramyar.martyrs.domain.Values;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.Set;
import java.util.UUID;

/** Request bodies of the editorial API and their mapping to domain values. */
final class EditorialRequests {
  private EditorialRequests() {}

  record DateBody(Integer year, Integer month, Integer day, Values.HistoricalDate.Calendar calendar) {
    Values.HistoricalDate toValue() { return year == null ? null : new Values.HistoricalDate(year, month, day, calendar); }
  }

  record LifeEventBody(DateBody date, String place) {
    Values.LifeEvent toValue() { return new Values.LifeEvent(date == null ? null : date.toValue(), place); }
  }

  record ContentBody(@NotBlank @Size(max = 160) String fullName, @Size(max = 80) String honorific, @NotBlank String contentLocale,
      @Size(max = 300) String shortDescription, @Size(max = Values.Biography.MAX) String biography, LifeEventBody birth, LifeEventBody martyrdom,
      @Size(max = 70) String seoTitle, @Size(max = 170) String seoDescription) {
    MartyrProfile.Content toValue() {
      return new MartyrProfile.Content(new Values.MartyrName(fullName, honorific), contentLocale, shortDescription, new Values.Biography(biography),
          birth == null ? null : birth.toValue(), martyrdom == null ? null : martyrdom.toValue(), new Values.SeoMetadata(seoTitle, seoDescription));
    }
  }

  record CreateProfile(@NotBlank String slug, @NotNull ContentBody content) {}

  record ReviseProfile(Long expectedVersion, String slug, @NotNull ContentBody content) {}

  record SourceBody(Long expectedVersion, @NotNull Values.SourceReference.Kind kind, @NotBlank String title, String author, String publisher, Integer year, String url, String note) {
    Values.SourceReference toValue() {
      URI parsed;
      try {
        parsed = url == null || url.isBlank() ? null : URI.create(url.strip());
      } catch (IllegalArgumentException malformed) {
        throw new IllegalArgumentException("Source URL is malformed");
      }
      return new Values.SourceReference(UUID.randomUUID(), kind, title, author, publisher, year, parsed, note);
    }
  }

  record StoryBody(Long expectedVersion, @NotBlank String title, @NotBlank String body, Set<UUID> sourceIds) {}

  record Transition(Long expectedVersion, @Size(max = 1000) String note) {}
}
