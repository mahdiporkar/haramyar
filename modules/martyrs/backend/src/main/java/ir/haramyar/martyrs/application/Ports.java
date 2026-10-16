package ir.haramyar.martyrs.application;

import ir.haramyar.martyrs.domain.MartyrProfile;
import ir.haramyar.martyrs.domain.Values.MartyrId;
import ir.haramyar.martyrs.domain.Values.MartyrSlug;
import ir.haramyar.martyrs.domain.Values.PublicationStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Outbound ports of the Martyrs application layer. */
public final class Ports {
  private Ports() {}

  public interface ProfileRepository {
    Optional<MartyrProfile> findById(MartyrId id);
    Optional<MartyrProfile> findBySlug(MartyrSlug slug);
    boolean slugTaken(MartyrSlug slug, MartyrId except);
    void add(MartyrProfile profile);
    /** @throws ConcurrentModification when the profile changed since it was loaded */
    void update(MartyrProfile profile);
  }

  /** Read model over published profiles only; drafts are structurally unreachable through it. */
  public interface PublishedProfiles {
    Page<PublicProfileSummary> search(String normalizedQuery, int page, int size);
    List<SitemapEntry> sitemap();
  }

  public interface EditorialIndex {
    Page<EditorialSummary> list(List<PublicationStatus> statuses, String normalizedQuery, int page, int size);
  }

  public record StoredMedia(UUID id, UUID profileId, String contentType, long sizeBytes, String sha256, String storageKey, UUID uploadedBy, Instant uploadedAt) {}

  public interface MediaCatalog {
    void add(StoredMedia media);
    Optional<StoredMedia> find(UUID mediaId);
  }

  /** Object storage for media bytes (S3-compatible). Credentials never leave the server. */
  public interface MediaStorage {
    void put(String key, byte[] bytes, String contentType);
    byte[] get(String key);
  }

  public static final class ConcurrentModification extends RuntimeException {
    public ConcurrentModification() { super("The profile changed concurrently; reload and retry"); }
  }

  public record Page<T>(List<T> items, int page, int size, long total) {
    public Page { items = List.copyOf(items); }
  }

  public record PublicProfileSummary(String slug, String fullName, String honorific, String shortDescription, UUID portraitMediaId,
      Integer birthYear, String birthCalendar, Integer martyrdomYear, String martyrdomCalendar, String contentLocale, Instant publishedAt) {}

  public record EditorialSummary(UUID id, String slug, String fullName, PublicationStatus status, Instant updatedAt, long version) {}

  public record SitemapEntry(String slug, Instant lastModified) {}
}
