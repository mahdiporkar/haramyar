package ir.haramyar.martyrs.application;

import ir.haramyar.martyrs.application.Ports.ConcurrentModification;
import ir.haramyar.martyrs.application.Ports.EditorialIndex;
import ir.haramyar.martyrs.application.Ports.EditorialSummary;
import ir.haramyar.martyrs.application.Ports.MediaCatalog;
import ir.haramyar.martyrs.application.Ports.MediaStorage;
import ir.haramyar.martyrs.application.Ports.Page;
import ir.haramyar.martyrs.application.Ports.ProfileRepository;
import ir.haramyar.martyrs.application.Ports.StoredMedia;
import ir.haramyar.martyrs.application.ProfileViews.EditorialProfile;
import ir.haramyar.martyrs.application.ProfileViews.MediaContent;
import ir.haramyar.martyrs.domain.EditorialPolicy;
import ir.haramyar.martyrs.domain.EditorialRuleViolation;
import ir.haramyar.martyrs.domain.MartyrProfile;
import ir.haramyar.martyrs.domain.Values.EditorialActor;
import ir.haramyar.martyrs.domain.Values.MartyrId;
import ir.haramyar.martyrs.domain.Values.MartyrSlug;
import ir.haramyar.martyrs.domain.Values.MediaReference;
import ir.haramyar.martyrs.domain.Values.PublicationStatus;
import ir.haramyar.martyrs.domain.Values.SourceReference;
import ir.haramyar.martyrs.domain.Values.Story;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Editorial use cases. Each loads the aggregate, invokes its behavior, persists in one transaction and returns the
 * editorial view. Editorial rules live in {@link MartyrProfile} and {@link EditorialPolicy}.
 */
public class EditorialHandlers {
  public enum MediaUsage { PORTRAIT, GALLERY }

  private final ProfileRepository profiles;
  private final EditorialIndex index;
  private final MediaCatalog mediaCatalog;
  private final MediaStorage storage;
  private final EditorialPolicy policy;
  private final TransactionTemplate transactions;
  private final Clock clock;

  public EditorialHandlers(ProfileRepository profiles, EditorialIndex index, MediaCatalog mediaCatalog, MediaStorage storage, EditorialPolicy policy,
      TransactionTemplate transactions, Clock clock) {
    this.profiles = profiles;
    this.index = index;
    this.mediaCatalog = mediaCatalog;
    this.storage = storage;
    this.policy = policy;
    this.transactions = transactions;
    this.clock = clock;
  }

  public EditorialProfile createDraft(EditorialActor editor, MartyrSlug slug, MartyrProfile.Content content) {
    return transactions.execute(status -> {
      if (profiles.slugTaken(slug, null)) throw slugTaken();
      MartyrProfile profile = MartyrProfile.createDraft(MartyrId.newId(), slug, content, editor, policy, clock.instant());
      profiles.add(profile);
      return EditorialProfile.of(profile, editor.userId());
    });
  }

  public EditorialProfile revise(EditorialActor editor, UUID profileId, Long expectedVersion, MartyrSlug slug, MartyrProfile.Content content) {
    return change(editor, profileId, expectedVersion, (profile, now) -> {
      if (slug != null && !slug.equals(profile.slug())) {
        if (profiles.slugTaken(slug, profile.id())) throw slugTaken();
        profile.changeSlug(editor, slug, policy, now);
      }
      profile.revise(editor, content, policy, now);
    });
  }

  public EditorialProfile addSource(EditorialActor editor, UUID profileId, Long expectedVersion, SourceReference source) {
    return change(editor, profileId, expectedVersion, (profile, now) -> profile.addSource(editor, source, policy, now));
  }

  public EditorialProfile removeSource(EditorialActor editor, UUID profileId, Long expectedVersion, UUID sourceId) {
    return change(editor, profileId, expectedVersion, (profile, now) -> profile.removeSource(editor, sourceId, policy, now));
  }

  public EditorialProfile putStory(EditorialActor editor, UUID profileId, Long expectedVersion, Story story) {
    return change(editor, profileId, expectedVersion, (profile, now) -> profile.putStory(editor, story, policy, now));
  }

  public EditorialProfile removeStory(EditorialActor editor, UUID profileId, Long expectedVersion, UUID storyId) {
    return change(editor, profileId, expectedVersion, (profile, now) -> profile.removeStory(editor, storyId, policy, now));
  }

  /**
   * Validates the image by content (type by magic bytes, size limit), stores it under a server-chosen key and attaches
   * it to the draft. The object is stored before the transaction; an aborted attachment leaves an unreferenced object
   * that is never served (media is served only through a profile that references it).
   */
  public EditorialProfile uploadMedia(EditorialActor editor, UUID profileId, Long expectedVersion, byte[] bytes, MediaUsage usage, String caption, String credit) {
    Objects.requireNonNull(usage, "usage");
    if (bytes == null || bytes.length == 0) throw new EditorialRuleViolation(EditorialRuleViolation.Code.INVALID_VALUE, "The file is empty");
    if (bytes.length > ImageInspector.MAX_BYTES) throw new EditorialRuleViolation(EditorialRuleViolation.Code.LIMIT_EXCEEDED, "Images may not exceed 5 MiB");
    ImageInspector.ImageType type = ImageInspector.detect(bytes)
        .orElseThrow(() -> new EditorialRuleViolation(EditorialRuleViolation.Code.INVALID_VALUE, "Only JPEG, PNG and WebP images are accepted"));
    MediaReference reference = new MediaReference(UUID.randomUUID(), caption, credit);
    // Fail fast before touching storage when the profile cannot accept media.
    transactions.executeWithoutResult(status -> load(profileId, expectedVersion));
    String key = "martyrs/" + profileId + "/" + reference.mediaId() + "." + type.extension;
    storage.put(key, bytes, type.contentType);
    return change(editor, profileId, expectedVersion, (profile, now) -> {
      mediaCatalog.add(new StoredMedia(reference.mediaId(), profileId, type.contentType, bytes.length, sha256(bytes), key, editor.userId(), now));
      if (usage == MediaUsage.PORTRAIT) profile.setPortrait(editor, reference, policy, now);
      else profile.addGalleryImage(editor, reference, policy, now);
    });
  }

  public EditorialProfile removeMedia(EditorialActor editor, UUID profileId, Long expectedVersion, UUID mediaId) {
    return change(editor, profileId, expectedVersion, (profile, now) -> profile.removeMedia(editor, mediaId, policy, now));
  }

  public EditorialProfile submitForReview(EditorialActor editor, UUID profileId, Long expectedVersion) {
    return change(editor, profileId, expectedVersion, (profile, now) -> profile.submitForReview(editor, policy, now));
  }

  public EditorialProfile requestChanges(EditorialActor reviewer, UUID profileId, Long expectedVersion, String note) {
    return change(reviewer, profileId, expectedVersion, (profile, now) -> profile.requestChanges(reviewer, note, policy, now));
  }

  public EditorialProfile publish(EditorialActor reviewer, UUID profileId, Long expectedVersion) {
    return change(reviewer, profileId, expectedVersion, (profile, now) -> profile.publish(reviewer, policy, now));
  }

  public EditorialProfile archive(EditorialActor reviewer, UUID profileId, Long expectedVersion, String reason) {
    return change(reviewer, profileId, expectedVersion, (profile, now) -> profile.archive(reviewer, reason, policy, now));
  }

  public EditorialProfile reopen(EditorialActor actor, UUID profileId, Long expectedVersion) {
    return change(actor, profileId, expectedVersion, (profile, now) -> profile.reopen(actor, policy, now));
  }

  public EditorialProfile get(EditorialActor viewer, UUID profileId) {
    return transactions.execute(status -> EditorialProfile.of(load(profileId, null), viewer.userId()));
  }

  public Page<EditorialSummary> list(List<PublicationStatus> statuses, String query, int page, int size) {
    return transactions.execute(status -> index.list(statuses, SearchText.normalize(query), Math.max(0, page), Math.min(Math.max(size, 1), 100)));
  }

  /** Editorial preview of any media of a profile, including drafts (staff only). */
  public MediaContent previewMedia(UUID mediaId) {
    StoredMedia media = transactions.execute(status -> mediaCatalog.find(mediaId))
        .orElseThrow(() -> new EditorialRuleViolation(EditorialRuleViolation.Code.NOT_FOUND, "Unknown media"));
    return new MediaContent(media.contentType(), storage.get(media.storageKey()), media.sha256());
  }

  private EditorialProfile change(EditorialActor actor, UUID profileId, Long expectedVersion, BiConsumer<MartyrProfile, Instant> behavior) {
    return transactions.execute(status -> {
      MartyrProfile profile = load(profileId, expectedVersion);
      behavior.accept(profile, clock.instant());
      profiles.update(profile);
      return EditorialProfile.of(profiles.findById(profile.id()).orElseThrow(), actor.userId());
    });
  }

  private MartyrProfile load(UUID profileId, Long expectedVersion) {
    MartyrProfile profile = profiles.findById(new MartyrId(profileId))
        .orElseThrow(() -> new EditorialRuleViolation(EditorialRuleViolation.Code.NOT_FOUND, "Unknown profile"));
    if (expectedVersion != null && expectedVersion != profile.version()) throw new ConcurrentModification();
    return profile;
  }

  static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private static EditorialRuleViolation slugTaken() {
    return new EditorialRuleViolation(EditorialRuleViolation.Code.INVALID_VALUE, "The slug is already used by another profile");
  }
}
