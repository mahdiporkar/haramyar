package ir.haramyar.martyrs.domain;

import ir.haramyar.martyrs.domain.EditorialPolicy.Operation;
import ir.haramyar.martyrs.domain.Values.Biography;
import ir.haramyar.martyrs.domain.Values.EditorialActor;
import ir.haramyar.martyrs.domain.Values.LifeEvent;
import ir.haramyar.martyrs.domain.Values.MartyrId;
import ir.haramyar.martyrs.domain.Values.MartyrName;
import ir.haramyar.martyrs.domain.Values.MartyrSlug;
import ir.haramyar.martyrs.domain.Values.MediaReference;
import ir.haramyar.martyrs.domain.Values.PublicationStatus;
import ir.haramyar.martyrs.domain.Values.SeoMetadata;
import ir.haramyar.martyrs.domain.Values.SourceReference;
import ir.haramyar.martyrs.domain.Values.Story;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Aggregate root of the Martyrs context: one martyr's biography with its sources, stories and media, and its editorial
 * lifecycle {@code DRAFT -> IN_REVIEW -> PUBLISHED -> ARCHIVED}. Content changes only while drafting; only published
 * profiles are ever public; publication requires sourced content and (by policy) a second person.
 */
public final class MartyrProfile {
  public static final int MAX_SOURCES = 100;
  public static final int MAX_STORIES = 50;
  public static final int MAX_GALLERY = 30;
  public static final Set<String> LOCALES = Set.of("fa", "ar", "en");

  /** Editable content of a draft. */
  public record Content(MartyrName name, String contentLocale, String shortDescription, Biography biography, LifeEvent birth, LifeEvent martyrdom, SeoMetadata seo) {
    public Content {
      Objects.requireNonNull(name, "name");
      if (contentLocale == null || !LOCALES.contains(contentLocale)) throw EditorialRuleViolation.invalid("Content language must be fa, ar or en");
      shortDescription = Text.optional(shortDescription, "Short description", 300);
      biography = biography == null ? new Biography(null) : biography;
      seo = seo == null ? new SeoMetadata(null, null) : seo;
    }
  }

  public record HistoryEntry(Operation operation, PublicationStatus from, PublicationStatus to, UUID actorId, EditorialActor.Role role, String note, Instant at) {}

  private final MartyrId id;
  private MartyrSlug slug;
  private Content content;
  private MediaReference portrait;
  private final List<MediaReference> gallery;
  private final List<SourceReference> sources;
  private final List<Story> stories;
  private PublicationStatus status;
  private final UUID createdBy;
  private UUID submittedBy;
  private final Instant createdAt;
  private Instant updatedAt;
  private Instant firstPublishedAt;
  private Instant publishedAt;
  private final long version;
  private final List<HistoryEntry> history;

  private MartyrProfile(MartyrId id, MartyrSlug slug, Content content, MediaReference portrait, List<MediaReference> gallery, List<SourceReference> sources,
      List<Story> stories, PublicationStatus status, UUID createdBy, UUID submittedBy, Instant createdAt, Instant updatedAt, Instant firstPublishedAt,
      Instant publishedAt, long version, List<HistoryEntry> history) {
    this.id = Objects.requireNonNull(id);
    this.slug = Objects.requireNonNull(slug);
    this.content = Objects.requireNonNull(content);
    this.portrait = portrait;
    this.gallery = new ArrayList<>(gallery);
    this.sources = new ArrayList<>(sources);
    this.stories = new ArrayList<>(stories);
    this.status = Objects.requireNonNull(status);
    this.createdBy = Objects.requireNonNull(createdBy);
    this.submittedBy = submittedBy;
    this.createdAt = Objects.requireNonNull(createdAt);
    this.updatedAt = Objects.requireNonNull(updatedAt);
    this.firstPublishedAt = firstPublishedAt;
    this.publishedAt = publishedAt;
    this.version = version;
    this.history = new ArrayList<>(history);
  }

  public static MartyrProfile createDraft(MartyrId id, MartyrSlug slug, Content content, EditorialActor editor, EditorialPolicy policy, Instant now) {
    authorize(Operation.CREATE, editor, policy);
    var profile = new MartyrProfile(id, slug, content, null, List.of(), List.of(), List.of(), PublicationStatus.DRAFT, editor.userId(), null, now, now, null, null, 0, List.of());
    profile.history.add(new HistoryEntry(Operation.CREATE, null, PublicationStatus.DRAFT, editor.userId(), editor.role(), null, now));
    return profile;
  }

  public static MartyrProfile reconstitute(MartyrId id, MartyrSlug slug, Content content, MediaReference portrait, List<MediaReference> gallery, List<SourceReference> sources,
      List<Story> stories, PublicationStatus status, UUID createdBy, UUID submittedBy, Instant createdAt, Instant updatedAt, Instant firstPublishedAt, Instant publishedAt,
      long version, List<HistoryEntry> history) {
    return new MartyrProfile(id, slug, content, portrait, gallery, sources, stories, status, createdBy, submittedBy, createdAt, updatedAt, firstPublishedAt, publishedAt, version, history);
  }

  // ---- drafting ---------------------------------------------------------------------------------------------------

  public void revise(EditorialActor editor, Content newContent, EditorialPolicy policy, Instant now) {
    editable(editor, policy);
    content = Objects.requireNonNull(newContent);
    touch(now);
  }

  /** The public URL may change only before the profile was ever published, so canonical links stay stable. */
  public void changeSlug(EditorialActor editor, MartyrSlug newSlug, EditorialPolicy policy, Instant now) {
    editable(editor, policy);
    if (firstPublishedAt != null && !newSlug.equals(slug))
      throw new EditorialRuleViolation(EditorialRuleViolation.Code.NOT_EDITABLE, "The slug of a profile that has been published cannot change");
    slug = newSlug;
    touch(now);
  }

  public void setPortrait(EditorialActor editor, MediaReference media, EditorialPolicy policy, Instant now) {
    editable(editor, policy);
    portrait = media;
    touch(now);
  }

  public void addGalleryImage(EditorialActor editor, MediaReference media, EditorialPolicy policy, Instant now) {
    editable(editor, policy);
    if (gallery.size() >= MAX_GALLERY) throw new EditorialRuleViolation(EditorialRuleViolation.Code.LIMIT_EXCEEDED, "At most " + MAX_GALLERY + " gallery images");
    gallery.add(Objects.requireNonNull(media));
    touch(now);
  }

  public void removeMedia(EditorialActor editor, UUID mediaId, EditorialPolicy policy, Instant now) {
    editable(editor, policy);
    if (portrait != null && portrait.mediaId().equals(mediaId)) portrait = null;
    gallery.removeIf(m -> m.mediaId().equals(mediaId));
    touch(now);
  }

  public void addSource(EditorialActor editor, SourceReference source, EditorialPolicy policy, Instant now) {
    editable(editor, policy);
    if (sources.size() >= MAX_SOURCES) throw new EditorialRuleViolation(EditorialRuleViolation.Code.LIMIT_EXCEEDED, "At most " + MAX_SOURCES + " sources");
    if (sources.stream().anyMatch(s -> s.id().equals(source.id()))) throw EditorialRuleViolation.invalid("Duplicate source id");
    sources.add(source);
    touch(now);
  }

  public void removeSource(EditorialActor editor, UUID sourceId, EditorialPolicy policy, Instant now) {
    editable(editor, policy);
    if (stories.stream().anyMatch(s -> s.sourceIds().contains(sourceId)))
      throw EditorialRuleViolation.invalid("The source is cited by a story; remove the citation first");
    if (!sources.removeIf(s -> s.id().equals(sourceId))) throw new EditorialRuleViolation(EditorialRuleViolation.Code.NOT_FOUND, "Unknown source");
    touch(now);
  }

  public void putStory(EditorialActor editor, Story story, EditorialPolicy policy, Instant now) {
    editable(editor, policy);
    for (UUID cited : story.sourceIds())
      if (sources.stream().noneMatch(s -> s.id().equals(cited))) throw EditorialRuleViolation.invalid("A story may cite only sources of this profile");
    int existing = indexOfStory(story.id());
    if (existing >= 0) stories.set(existing, story);
    else {
      if (stories.size() >= MAX_STORIES) throw new EditorialRuleViolation(EditorialRuleViolation.Code.LIMIT_EXCEEDED, "At most " + MAX_STORIES + " stories");
      stories.add(story);
    }
    touch(now);
  }

  public void removeStory(EditorialActor editor, UUID storyId, EditorialPolicy policy, Instant now) {
    editable(editor, policy);
    if (!stories.removeIf(s -> s.id().equals(storyId))) throw new EditorialRuleViolation(EditorialRuleViolation.Code.NOT_FOUND, "Unknown story");
    touch(now);
  }

  // ---- editorial lifecycle ----------------------------------------------------------------------------------------

  /** Content must be source-supported before anyone reviews it. */
  public void submitForReview(EditorialActor editor, EditorialPolicy policy, Instant now) {
    authorize(Operation.SUBMIT, editor, policy);
    require(PublicationStatus.DRAFT, "Only drafts can be submitted for review");
    var missing = new ArrayList<String>();
    if (content.shortDescription() == null) missing.add("short description");
    if (content.biography().isEmpty()) missing.add("biography");
    if (sources.isEmpty()) missing.add("at least one source");
    if (!missing.isEmpty()) throw new EditorialRuleViolation(EditorialRuleViolation.Code.NOT_READY_FOR_REVIEW, "Missing: " + String.join(", ", missing));
    submittedBy = editor.userId();
    move(Operation.SUBMIT, PublicationStatus.IN_REVIEW, editor, null, now);
  }

  public void requestChanges(EditorialActor reviewer, String note, EditorialPolicy policy, Instant now) {
    authorize(Operation.REQUEST_CHANGES, reviewer, policy);
    require(PublicationStatus.IN_REVIEW, "Only profiles in review can be returned");
    move(Operation.REQUEST_CHANGES, PublicationStatus.DRAFT, reviewer, Text.required(note, "Review note", 3, 1000), now);
  }

  public void publish(EditorialActor reviewer, EditorialPolicy policy, Instant now) {
    authorize(Operation.PUBLISH, reviewer, policy);
    require(PublicationStatus.IN_REVIEW, "Only reviewed profiles can be published");
    if (policy.fourEyes() && reviewer.userId().equals(submittedBy))
      throw new EditorialRuleViolation(EditorialRuleViolation.Code.FOUR_EYES_REQUIRED, "A second person must publish what you submitted");
    if (firstPublishedAt == null) firstPublishedAt = now;
    publishedAt = now;
    move(Operation.PUBLISH, PublicationStatus.PUBLISHED, reviewer, null, now);
  }

  /** Unpublishes: the profile disappears from every public surface but keeps its history. */
  public void archive(EditorialActor reviewer, String reason, EditorialPolicy policy, Instant now) {
    authorize(Operation.ARCHIVE, reviewer, policy);
    require(PublicationStatus.PUBLISHED, "Only published profiles can be archived");
    move(Operation.ARCHIVE, PublicationStatus.ARCHIVED, reviewer, Text.optional(reason, "Reason", 500), now);
  }

  public void reopen(EditorialActor actor, EditorialPolicy policy, Instant now) {
    authorize(Operation.REOPEN, actor, policy);
    require(PublicationStatus.ARCHIVED, "Only archived profiles can be reopened");
    move(Operation.REOPEN, PublicationStatus.DRAFT, actor, null, now);
  }

  public boolean isPublic() { return status == PublicationStatus.PUBLISHED; }

  /** Whether a media object belongs to the published presentation of this profile. */
  public boolean publiclyShows(UUID mediaId) {
    return isPublic() && ((portrait != null && portrait.mediaId().equals(mediaId)) || gallery.stream().anyMatch(m -> m.mediaId().equals(mediaId)));
  }

  private void editable(EditorialActor editor, EditorialPolicy policy) {
    authorize(Operation.REVISE, editor, policy);
    if (status != PublicationStatus.DRAFT)
      throw new EditorialRuleViolation(EditorialRuleViolation.Code.NOT_EDITABLE, "Only drafts can be edited; a " + status + " profile must be returned or reopened first");
  }

  private static void authorize(Operation operation, EditorialActor actor, EditorialPolicy policy) {
    Objects.requireNonNull(actor, "actor");
    if (!policy.permits(operation, actor.role()))
      throw new EditorialRuleViolation(EditorialRuleViolation.Code.ACTOR_NOT_PERMITTED, actor.role() + " may not " + operation);
  }

  private void require(PublicationStatus expected, String message) {
    if (status != expected) throw new EditorialRuleViolation(EditorialRuleViolation.Code.INVALID_TRANSITION, message);
  }

  private void move(Operation operation, PublicationStatus next, EditorialActor actor, String note, Instant now) {
    PublicationStatus previous = status;
    status = next;
    touch(now);
    history.add(new HistoryEntry(operation, previous, next, actor.userId(), actor.role(), note, now));
  }

  private void touch(Instant now) { updatedAt = now; }

  private int indexOfStory(UUID storyId) {
    for (int i = 0; i < stories.size(); i++) if (stories.get(i).id().equals(storyId)) return i;
    return -1;
  }

  public MartyrId id() { return id; }
  public MartyrSlug slug() { return slug; }
  public Content content() { return content; }
  public MediaReference portrait() { return portrait; }
  public List<MediaReference> gallery() { return List.copyOf(gallery); }
  public List<SourceReference> sources() { return List.copyOf(sources); }
  public List<Story> stories() { return List.copyOf(stories); }
  public PublicationStatus status() { return status; }
  public UUID createdBy() { return createdBy; }
  public UUID submittedBy() { return submittedBy; }
  public Instant createdAt() { return createdAt; }
  public Instant updatedAt() { return updatedAt; }
  public Instant firstPublishedAt() { return firstPublishedAt; }
  public Instant publishedAt() { return publishedAt; }
  public long version() { return version; }
  public List<HistoryEntry> history() { return List.copyOf(history); }
}
