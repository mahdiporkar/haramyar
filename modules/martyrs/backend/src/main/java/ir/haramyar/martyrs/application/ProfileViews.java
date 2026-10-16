package ir.haramyar.martyrs.application;

import ir.haramyar.martyrs.domain.EditorialPolicy;
import ir.haramyar.martyrs.domain.MartyrProfile;
import ir.haramyar.martyrs.domain.Values.EditorialActor;
import ir.haramyar.martyrs.domain.Values.LifeEvent;
import ir.haramyar.martyrs.domain.Values.MediaReference;
import ir.haramyar.martyrs.domain.Values.PublicationStatus;
import ir.haramyar.martyrs.domain.Values.SeoMetadata;
import ir.haramyar.martyrs.domain.Values.SourceReference;
import ir.haramyar.martyrs.domain.Values.Story;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read representations. The public view carries no editorial metadata (who drafted, reviewed or when submitted). */
public final class ProfileViews {
  private ProfileViews() {}

  public record PublicProfile(String slug, String fullName, String honorific, String contentLocale, String shortDescription, String biography,
      LifeEvent birth, LifeEvent martyrdom, MediaReference portrait, List<MediaReference> gallery, List<SourceReference> sources, List<Story> stories,
      SeoMetadata seo, Instant firstPublishedAt, Instant updatedAt) {
    public static PublicProfile of(MartyrProfile p) {
      var c = p.content();
      return new PublicProfile(p.slug().value(), c.name().fullName(), c.name().honorific(), c.contentLocale(), c.shortDescription(), c.biography().text(),
          c.birth(), c.martyrdom(), p.portrait(), p.gallery(), p.sources(), p.stories(), c.seo(), p.firstPublishedAt(), p.publishedAt());
    }
  }

  public record History(EditorialPolicy.Operation operation, PublicationStatus from, PublicationStatus to, EditorialActor.Role role, boolean byViewer, String note, Instant at) {}

  public record EditorialProfile(UUID id, String slug, PublicationStatus status, MartyrProfile.Content content, MediaReference portrait, List<MediaReference> gallery,
      List<SourceReference> sources, List<Story> stories, boolean submittedByViewer, Instant createdAt, Instant updatedAt, Instant firstPublishedAt,
      Instant publishedAt, long version, List<History> history) {
    public static EditorialProfile of(MartyrProfile p, UUID viewer) {
      return new EditorialProfile(p.id().value(), p.slug().value(), p.status(), p.content(), p.portrait(), p.gallery(), p.sources(), p.stories(),
          viewer.equals(p.submittedBy()), p.createdAt(), p.updatedAt(), p.firstPublishedAt(), p.publishedAt(), p.version(),
          p.history().stream().map(h -> new History(h.operation(), h.from(), h.to(), h.role(), h.actorId().equals(viewer), h.note(), h.at())).toList());
    }
  }

  public record MediaContent(String contentType, byte[] bytes, String sha256) {}
}
