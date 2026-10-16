package ir.haramyar.martyrs.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import ir.haramyar.martyrs.application.Ports;
import ir.haramyar.martyrs.application.Ports.ConcurrentModification;
import ir.haramyar.martyrs.application.SearchText;
import ir.haramyar.martyrs.domain.EditorialPolicy;
import ir.haramyar.martyrs.domain.MartyrProfile;
import ir.haramyar.martyrs.domain.Values.Biography;
import ir.haramyar.martyrs.domain.Values.EditorialActor;
import ir.haramyar.martyrs.domain.Values.HistoricalDate;
import ir.haramyar.martyrs.domain.Values.LifeEvent;
import ir.haramyar.martyrs.domain.Values.MartyrId;
import ir.haramyar.martyrs.domain.Values.MartyrName;
import ir.haramyar.martyrs.domain.Values.MartyrSlug;
import ir.haramyar.martyrs.domain.Values.MediaReference;
import ir.haramyar.martyrs.domain.Values.PublicationStatus;
import ir.haramyar.martyrs.domain.Values.SeoMetadata;
import ir.haramyar.martyrs.domain.Values.SourceReference;
import ir.haramyar.martyrs.domain.Values.Story;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

/** Maps {@link MartyrProfile} to its row; value collections become versionable JSON documents (persistence DTOs). */
@Repository
class JpaProfileRepository implements Ports.ProfileRepository {
  record DateDoc(Integer year, Integer month, Integer day, String calendar) {}
  record LifeEventDoc(DateDoc date, String place) {}
  record MediaDoc(UUID mediaId, String caption, String credit) {}
  record SourceDoc(UUID id, String kind, String title, String author, String publisher, Integer year, String url, String note) {}
  record StoryDoc(UUID id, String title, String body, Set<UUID> sourceIds) {}

  private final ProfileJpaRepository rows;
  private final HistoryJpaRepository historyRows;
  private final ObjectMapper json;

  JpaProfileRepository(ProfileJpaRepository rows, HistoryJpaRepository historyRows, ObjectMapper json) {
    this.rows = rows;
    this.historyRows = historyRows;
    this.json = json;
  }

  @Override public Optional<MartyrProfile> findById(MartyrId id) { return rows.findById(id.value()).map(this::toDomain); }

  @Override public Optional<MartyrProfile> findBySlug(MartyrSlug slug) { return rows.findBySlug(slug.value()).map(this::toDomain); }

  @Override public boolean slugTaken(MartyrSlug slug, MartyrId except) {
    return except == null ? rows.existsBySlug(slug.value()) : rows.existsBySlugAndIdNot(slug.value(), except.value());
  }

  @Override
  public void add(MartyrProfile profile) {
    var entity = new Entities.ProfileEntity();
    entity.id = profile.id().value();
    entity.createdBy = profile.createdBy();
    entity.createdAt = profile.createdAt();
    copy(profile, entity);
    rows.saveAndFlush(entity);
    appendHistory(profile, 0);
  }

  @Override
  public void update(MartyrProfile profile) {
    var entity = rows.findById(profile.id().value()).orElseThrow(ConcurrentModification::new);
    if (entity.version != profile.version()) throw new ConcurrentModification();
    int persisted = historyRows.findByProfileIdOrderBySequenceNo(entity.id).size();
    copy(profile, entity);
    try {
      rows.saveAndFlush(entity);
    } catch (OptimisticLockingFailureException stale) {
      throw new ConcurrentModification();
    }
    appendHistory(profile, persisted);
  }

  private void copy(MartyrProfile p, Entities.ProfileEntity e) {
    var c = p.content();
    e.slug = p.slug().value();
    e.status = p.status().name();
    e.fullName = c.name().fullName();
    e.honorific = c.name().honorific();
    e.contentLocale = c.contentLocale();
    e.shortDescription = c.shortDescription();
    e.biography = c.biography().text();
    e.birth = write(lifeEvent(c.birth()));
    e.martyrdom = write(lifeEvent(c.martyrdom()));
    e.seoTitle = c.seo().title();
    e.seoDescription = c.seo().description();
    e.portrait = p.portrait() == null ? null : write(media(p.portrait()));
    e.gallery = write(p.gallery().stream().map(JpaProfileRepository::media).toList());
    e.sources = write(p.sources().stream().map(s -> new SourceDoc(s.id(), s.kind().name(), s.title(), s.author(), s.publisher(), s.year(), s.url() == null ? null : s.url().toString(), s.note())).toList());
    e.stories = write(p.stories().stream().map(s -> new StoryDoc(s.id(), s.title(), s.body(), s.sourceIds())).toList());
    e.submittedBy = p.submittedBy();
    e.updatedAt = p.updatedAt();
    e.firstPublishedAt = p.firstPublishedAt();
    e.publishedAt = p.publishedAt();
    e.searchText = SearchText.normalize(String.join(" ", c.name().fullName(), c.name().honorific() == null ? "" : c.name().honorific(),
        c.shortDescription() == null ? "" : c.shortDescription(), p.slug().value()));
  }

  private void appendHistory(MartyrProfile profile, int alreadyPersisted) {
    var history = profile.history();
    for (int i = alreadyPersisted; i < history.size(); i++) {
      var h = history.get(i);
      var row = new Entities.HistoryEntity();
      row.profileId = profile.id().value();
      row.sequenceNo = i + 1;
      row.operation = h.operation().name();
      row.fromStatus = h.from() == null ? null : h.from().name();
      row.toStatus = h.to().name();
      row.actorId = h.actorId();
      row.actorRole = h.role().name();
      row.note = h.note();
      row.occurredAt = h.at();
      historyRows.save(row);
    }
    historyRows.flush();
  }

  private MartyrProfile toDomain(Entities.ProfileEntity e) {
    var content = new MartyrProfile.Content(new MartyrName(e.fullName, e.honorific), e.contentLocale, e.shortDescription, new Biography(e.biography),
        lifeEvent(read(e.birth, LifeEventDoc.class)), lifeEvent(read(e.martyrdom, LifeEventDoc.class)), new SeoMetadata(e.seoTitle, e.seoDescription));
    MediaDoc portrait = read(e.portrait, MediaDoc.class);
    List<MediaReference> gallery = readList(e.gallery, new TypeReference<List<MediaDoc>>() {}).stream().map(JpaProfileRepository::media).toList();
    List<SourceReference> sources = readList(e.sources, new TypeReference<List<SourceDoc>>() {}).stream()
        .map(s -> new SourceReference(s.id(), SourceReference.Kind.valueOf(s.kind()), s.title(), s.author(), s.publisher(), s.year(), s.url() == null ? null : URI.create(s.url()), s.note())).toList();
    List<Story> stories = readList(e.stories, new TypeReference<List<StoryDoc>>() {}).stream().map(s -> new Story(s.id(), s.title(), s.body(), s.sourceIds())).toList();
    var history = historyRows.findByProfileIdOrderBySequenceNo(e.id).stream()
        .map(h -> new MartyrProfile.HistoryEntry(EditorialPolicy.Operation.valueOf(h.operation), h.fromStatus == null ? null : PublicationStatus.valueOf(h.fromStatus),
            PublicationStatus.valueOf(h.toStatus), h.actorId, EditorialActor.Role.valueOf(h.actorRole), h.note, h.occurredAt)).toList();
    return MartyrProfile.reconstitute(new MartyrId(e.id), new MartyrSlug(e.slug), content, portrait == null ? null : media(portrait), gallery, sources, stories,
        PublicationStatus.valueOf(e.status), e.createdBy, e.submittedBy, e.createdAt, e.updatedAt, e.firstPublishedAt, e.publishedAt, e.version, history);
  }

  private static MediaDoc media(MediaReference m) { return new MediaDoc(m.mediaId(), m.caption(), m.credit()); }
  private static MediaReference media(MediaDoc m) { return new MediaReference(m.mediaId(), m.caption(), m.credit()); }

  private static LifeEventDoc lifeEvent(LifeEvent event) {
    if (event == null || event.isEmpty()) return null;
    var d = event.date();
    return new LifeEventDoc(d == null ? null : new DateDoc(d.year(), d.month(), d.day(), d.calendar().name()), event.place());
  }

  private static LifeEvent lifeEvent(LifeEventDoc doc) {
    if (doc == null) return null;
    var d = doc.date();
    return new LifeEvent(d == null ? null : new HistoricalDate(d.year(), d.month(), d.day(), HistoricalDate.Calendar.valueOf(d.calendar())), doc.place());
  }

  private String write(Object value) {
    if (value == null) return null;
    try {
      return json.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  private <T> T read(String value, Class<T> type) {
    if (value == null) return null;
    try {
      return json.readValue(value, type);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  private <T> List<T> readList(String value, TypeReference<List<T>> type) {
    if (value == null) return List.of();
    try {
      return json.readValue(value, type);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }
}
