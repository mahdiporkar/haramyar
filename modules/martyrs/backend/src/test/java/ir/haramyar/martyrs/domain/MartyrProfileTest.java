package ir.haramyar.martyrs.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ir.haramyar.martyrs.domain.EditorialRuleViolation.Code;
import ir.haramyar.martyrs.domain.Values.Biography;
import ir.haramyar.martyrs.domain.Values.EditorialActor;
import ir.haramyar.martyrs.domain.Values.HistoricalDate;
import ir.haramyar.martyrs.domain.Values.LifeEvent;
import ir.haramyar.martyrs.domain.Values.MartyrId;
import ir.haramyar.martyrs.domain.Values.MartyrName;
import ir.haramyar.martyrs.domain.Values.MartyrSlug;
import ir.haramyar.martyrs.domain.Values.MediaReference;
import ir.haramyar.martyrs.domain.Values.PublicationStatus;
import ir.haramyar.martyrs.domain.Values.SourceReference;
import ir.haramyar.martyrs.domain.Values.Story;
import java.net.URI;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

/** Editorial lifecycle rules. All names and texts are synthetic development fixtures, not historical claims. */
class MartyrProfileTest {
  static final Instant NOW = Instant.parse("2026-10-16T08:00:00Z");
  final EditorialPolicy policy = new EditorialPolicy(true);
  final EditorialActor editor = new EditorialActor(UUID.randomUUID(), EditorialActor.Role.EDITOR);
  final EditorialActor reviewer = new EditorialActor(UUID.randomUUID(), EditorialActor.Role.REVIEWER);

  static void violates(Code code, ThrowingCallable action) {
    assertThatThrownBy(action).isInstanceOf(EditorialRuleViolation.class).extracting(e -> ((EditorialRuleViolation) e).code()).isEqualTo(code);
  }

  static MartyrProfile.Content content(String shortDescription, String biography) {
    return new MartyrProfile.Content(new MartyrName("Fixture Profile (development)", null), "fa", shortDescription, new Biography(biography),
        new LifeEvent(new HistoricalDate(1340, null, null, HistoricalDate.Calendar.SOLAR_HIJRI), "Fixture place"), null, null);
  }

  MartyrProfile draft() {
    return MartyrProfile.createDraft(MartyrId.newId(), new MartyrSlug("fixture-profile"), content(null, null), editor, policy, NOW);
  }

  MartyrProfile ready() {
    MartyrProfile profile = draft();
    profile.revise(editor, content("Synthetic summary for tests", "Synthetic biography paragraph for tests."), policy, NOW);
    profile.addSource(editor, source(), policy, NOW);
    return profile;
  }

  static SourceReference source() {
    return new SourceReference(UUID.randomUUID(), SourceReference.Kind.ARCHIVE, "Fixture archive record", null, "Fixture archive", 1390, URI.create("https://example.org/record/1"), null);
  }

  @Test
  void draftsStartPrivateAndRecordHistory() {
    MartyrProfile profile = draft();
    assertThat(profile.status()).isEqualTo(PublicationStatus.DRAFT);
    assertThat(profile.isPublic()).isFalse();
    assertThat(profile.history()).singleElement().extracting(MartyrProfile.HistoryEntry::operation).isEqualTo(EditorialPolicy.Operation.CREATE);
  }

  @Test
  void onlyEditorsCreateAndRevise() {
    violates(Code.ACTOR_NOT_PERMITTED, () -> MartyrProfile.createDraft(MartyrId.newId(), new MartyrSlug("x-y-z"), content(null, null), reviewer, policy, NOW));
    violates(Code.ACTOR_NOT_PERMITTED, () -> draft().revise(reviewer, content("a summary", null), policy, NOW));
  }

  @Test
  void submissionRequiresSourceSupportedContent() {
    MartyrProfile profile = draft();
    assertThatThrownBy(() -> profile.submitForReview(editor, policy, NOW)).isInstanceOf(EditorialRuleViolation.class)
        .hasMessageContaining("short description").hasMessageContaining("biography").hasMessageContaining("source");
    profile.revise(editor, content("Synthetic summary", "Synthetic biography."), policy, NOW);
    violates(Code.NOT_READY_FOR_REVIEW, () -> profile.submitForReview(editor, policy, NOW));
    profile.addSource(editor, source(), policy, NOW);
    profile.submitForReview(editor, policy, NOW);
    assertThat(profile.status()).isEqualTo(PublicationStatus.IN_REVIEW);
    assertThat(profile.isPublic()).isFalse();
  }

  @Test
  void reviewIsRequiredAndFourEyesEnforced() {
    MartyrProfile profile = ready();
    violates(Code.INVALID_TRANSITION, () -> profile.publish(reviewer, policy, NOW));
    profile.submitForReview(editor, policy, NOW);
    violates(Code.ACTOR_NOT_PERMITTED, () -> profile.publish(editor, policy, NOW));
    EditorialActor sameAsSubmitter = new EditorialActor(editor.userId(), EditorialActor.Role.REVIEWER);
    violates(Code.FOUR_EYES_REQUIRED, () -> profile.publish(sameAsSubmitter, policy, NOW));
    profile.publish(reviewer, policy, NOW);
    assertThat(profile.status()).isEqualTo(PublicationStatus.PUBLISHED);
    assertThat(profile.isPublic()).isTrue();
    assertThat(profile.publishedAt()).isEqualTo(NOW);
  }

  @Test
  void fourEyesIsAConfigurablePolicy() {
    MartyrProfile profile = ready();
    profile.submitForReview(editor, policy, NOW);
    profile.publish(new EditorialActor(editor.userId(), EditorialActor.Role.REVIEWER), new EditorialPolicy(false), NOW);
    assertThat(profile.isPublic()).isTrue();
  }

  @Test
  void reviewerCanReturnWithNote() {
    MartyrProfile profile = ready();
    profile.submitForReview(editor, policy, NOW);
    violates(Code.NOT_EDITABLE, () -> profile.revise(editor, content("x x x", "y"), policy, NOW));
    violates(Code.INVALID_VALUE, () -> profile.requestChanges(reviewer, "", policy, NOW));
    profile.requestChanges(reviewer, "Please cite the interview", policy, NOW);
    assertThat(profile.status()).isEqualTo(PublicationStatus.DRAFT);
    assertThat(profile.history()).last().extracting(MartyrProfile.HistoryEntry::note).isEqualTo("Please cite the interview");
  }

  @Test
  void publishedContentIsFrozenAndArchivingUnpublishes() {
    MartyrProfile profile = ready();
    profile.submitForReview(editor, policy, NOW);
    profile.publish(reviewer, policy, NOW);
    violates(Code.NOT_EDITABLE, () -> profile.revise(editor, content("changed", "changed"), policy, NOW));
    violates(Code.NOT_EDITABLE, () -> profile.addSource(editor, source(), policy, NOW));
    violates(Code.ACTOR_NOT_PERMITTED, () -> profile.archive(editor, null, policy, NOW));
    profile.archive(reviewer, "Under re-verification", policy, NOW);
    assertThat(profile.isPublic()).isFalse();
    profile.reopen(editor, policy, NOW);
    assertThat(profile.status()).isEqualTo(PublicationStatus.DRAFT);
    violates(Code.NOT_EDITABLE, () -> profile.changeSlug(editor, new MartyrSlug("another-slug"), policy, NOW));
  }

  @Test
  void slugCanChangeBeforeFirstPublication() {
    MartyrProfile profile = draft();
    profile.changeSlug(editor, new MartyrSlug("better-slug"), policy, NOW);
    assertThat(profile.slug().value()).isEqualTo("better-slug");
    violates(Code.INVALID_VALUE, () -> new MartyrSlug("Bad Slug"));
    violates(Code.INVALID_VALUE, () -> new MartyrSlug("double--hyphen"));
    violates(Code.INVALID_VALUE, () -> new MartyrSlug("ab"));
  }

  @Test
  void storiesMayCiteOnlyOwnSourcesAndCitedSourcesCannotBeRemoved() {
    MartyrProfile profile = ready();
    UUID sourceId = profile.sources().getFirst().id();
    violates(Code.INVALID_VALUE, () -> profile.putStory(editor, new Story(UUID.randomUUID(), "Fixture story", "A synthetic narrative used only in tests.", Set.of(UUID.randomUUID())), policy, NOW));
    Story story = new Story(UUID.randomUUID(), "Fixture story", "A synthetic narrative used only in tests.", Set.of(sourceId));
    profile.putStory(editor, story, policy, NOW);
    violates(Code.INVALID_VALUE, () -> profile.removeSource(editor, sourceId, policy, NOW));
    profile.putStory(editor, new Story(story.id(), "Renamed story", story.body(), Set.of()), policy, NOW);
    assertThat(profile.stories()).singleElement().extracting(Story::title).isEqualTo("Renamed story");
    profile.removeSource(editor, sourceId, policy, NOW);
    assertThat(profile.sources()).isEmpty();
  }

  @Test
  void onlyPublishedProfilesExposeTheirMedia() {
    MartyrProfile profile = ready();
    UUID portrait = UUID.randomUUID();
    UUID unrelated = UUID.randomUUID();
    profile.setPortrait(editor, new MediaReference(portrait, "Fixture caption", null), policy, NOW);
    assertThat(profile.publiclyShows(portrait)).isFalse();
    profile.submitForReview(editor, policy, NOW);
    profile.publish(reviewer, policy, NOW);
    assertThat(profile.publiclyShows(portrait)).isTrue();
    assertThat(profile.publiclyShows(unrelated)).isFalse();
    profile.archive(reviewer, null, policy, NOW);
    assertThat(profile.publiclyShows(portrait)).isFalse();
  }

  @Test
  void valuesRejectUnsafeInput() {
    violates(Code.INVALID_VALUE, () -> new SourceReference(UUID.randomUUID(), SourceReference.Kind.WEBSITE, "Title", null, null, null, URI.create("javascript:alert(1)"), null));
    violates(Code.INVALID_VALUE, () -> new SourceReference(UUID.randomUUID(), SourceReference.Kind.WEBSITE, "Title", null, null, null, URI.create("https://user:pw@example.org/"), null));
    violates(Code.INVALID_VALUE, () -> new HistoricalDate(1360, null, 5, HistoricalDate.Calendar.SOLAR_HIJRI));
    violates(Code.INVALID_VALUE, () -> new MartyrProfile.Content(new MartyrName("Name", null), "de", null, null, null, null, null));
    violates(Code.INVALID_VALUE, () -> new MartyrName("Bad\u0007Name", null));
  }
}
