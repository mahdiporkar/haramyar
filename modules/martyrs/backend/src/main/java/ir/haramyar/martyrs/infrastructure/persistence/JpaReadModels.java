package ir.haramyar.martyrs.infrastructure.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ir.haramyar.martyrs.application.Ports;
import ir.haramyar.martyrs.domain.Values.PublicationStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/** Query-side adapters: published search and sitemap (published rows only), editorial index, media catalog. */
@Component
class JpaReadModels implements Ports.PublishedProfiles, Ports.EditorialIndex, Ports.MediaCatalog {
  private final ProfileJpaRepository rows;
  private final MediaJpaRepository media;
  private final ObjectMapper json;

  JpaReadModels(ProfileJpaRepository rows, MediaJpaRepository media, ObjectMapper json) {
    this.rows = rows;
    this.media = media;
    this.json = json;
  }

  @Override
  public Ports.Page<Ports.PublicProfileSummary> search(String normalizedQuery, int page, int size) {
    var result = rows.searchPublished(normalizedQuery, PageRequest.of(page, size));
    return new Ports.Page<>(result.getContent().stream().map(this::summary).toList(), page, size, result.getTotalElements());
  }

  @Override
  public List<Ports.SitemapEntry> sitemap() {
    return rows.published().stream().map(p -> new Ports.SitemapEntry(p.slug, p.publishedAt)).toList();
  }

  @Override
  public Ports.Page<Ports.EditorialSummary> list(List<PublicationStatus> statuses, String normalizedQuery, int page, int size) {
    List<String> names = (statuses == null || statuses.isEmpty() ? List.of(PublicationStatus.values()) : statuses).stream().map(Enum::name).toList();
    var result = rows.editorialIndex(names, normalizedQuery, PageRequest.of(page, size));
    return new Ports.Page<>(result.getContent().stream().map(p -> new Ports.EditorialSummary(p.id, p.slug, p.fullName, PublicationStatus.valueOf(p.status), p.updatedAt, p.version)).toList(),
        page, size, result.getTotalElements());
  }

  @Override
  public void add(Ports.StoredMedia m) {
    var e = new Entities.MediaEntity();
    e.id = m.id();
    e.profileId = m.profileId();
    e.contentType = m.contentType();
    e.sizeBytes = m.sizeBytes();
    e.sha256 = m.sha256();
    e.storageKey = m.storageKey();
    e.uploadedBy = m.uploadedBy();
    e.uploadedAt = m.uploadedAt();
    media.saveAndFlush(e);
  }

  @Override
  public Optional<Ports.StoredMedia> find(UUID mediaId) {
    return media.findById(mediaId).map(e -> new Ports.StoredMedia(e.id, e.profileId, e.contentType, e.sizeBytes, e.sha256, e.storageKey, e.uploadedBy, e.uploadedAt));
  }

  private Ports.PublicProfileSummary summary(Entities.ProfileEntity p) {
    JsonNode birth = node(p.birth);
    JsonNode martyrdom = node(p.martyrdom);
    JsonNode portrait = node(p.portrait);
    return new Ports.PublicProfileSummary(p.slug, p.fullName, p.honorific, p.shortDescription,
        portrait == null ? null : UUID.fromString(portrait.path("mediaId").asText()),
        year(birth), calendar(birth), year(martyrdom), calendar(martyrdom), p.contentLocale, p.publishedAt);
  }

  private JsonNode node(String value) {
    try {
      return value == null ? null : json.readTree(value);
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  private static Integer year(JsonNode event) {
    return event == null || event.path("date").isMissingNode() || event.path("date").isNull() ? null : event.path("date").path("year").asInt();
  }

  private static String calendar(JsonNode event) {
    return event == null || event.path("date").isMissingNode() || event.path("date").isNull() ? null : event.path("date").path("calendar").asText();
  }
}
