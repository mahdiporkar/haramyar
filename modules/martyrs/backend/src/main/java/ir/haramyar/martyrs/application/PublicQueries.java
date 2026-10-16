package ir.haramyar.martyrs.application;

import ir.haramyar.martyrs.application.Ports.MediaCatalog;
import ir.haramyar.martyrs.application.Ports.MediaStorage;
import ir.haramyar.martyrs.application.Ports.Page;
import ir.haramyar.martyrs.application.Ports.ProfileRepository;
import ir.haramyar.martyrs.application.Ports.PublicProfileSummary;
import ir.haramyar.martyrs.application.Ports.PublishedProfiles;
import ir.haramyar.martyrs.application.Ports.SitemapEntry;
import ir.haramyar.martyrs.application.ProfileViews.MediaContent;
import ir.haramyar.martyrs.application.ProfileViews.PublicProfile;
import ir.haramyar.martyrs.domain.Values.MartyrId;
import ir.haramyar.martyrs.domain.Values.MartyrSlug;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Anonymous read access. Every path checks publication: an unknown, draft, in-review or archived profile and media not
 * shown by a published profile are all indistinguishable "not found".
 */
public class PublicQueries {
  private final ProfileRepository profiles;
  private final PublishedProfiles published;
  private final MediaCatalog mediaCatalog;
  private final MediaStorage storage;
  private final TransactionTemplate readOnly;

  public PublicQueries(ProfileRepository profiles, PublishedProfiles published, MediaCatalog mediaCatalog, MediaStorage storage, TransactionTemplate readOnly) {
    this.profiles = profiles;
    this.published = published;
    this.mediaCatalog = mediaCatalog;
    this.storage = storage;
    this.readOnly = readOnly;
  }

  public Page<PublicProfileSummary> search(String query, int page, int size) {
    if (query != null && query.length() > 100) throw new IllegalArgumentException("Query too long");
    return readOnly.execute(status -> published.search(SearchText.normalize(query), Math.max(0, page), Math.min(Math.max(size, 1), 50)));
  }

  public Optional<PublicProfile> bySlug(String slug) {
    MartyrSlug parsed;
    try {
      parsed = new MartyrSlug(slug);
    } catch (RuntimeException malformed) {
      return Optional.empty();
    }
    return readOnly.execute(status -> profiles.findBySlug(parsed).filter(p -> p.isPublic()).map(PublicProfile::of));
  }

  public Optional<MediaContent> media(UUID mediaId) {
    return readOnly.execute(status -> mediaCatalog.find(mediaId)
        .filter(m -> profiles.findById(new MartyrId(m.profileId())).map(p -> p.publiclyShows(mediaId)).orElse(false)))
        .map(m -> new MediaContent(m.contentType(), storage.get(m.storageKey()), m.sha256()));
  }

  public List<SitemapEntry> sitemap() {
    return readOnly.execute(status -> published.sitemap());
  }
}
