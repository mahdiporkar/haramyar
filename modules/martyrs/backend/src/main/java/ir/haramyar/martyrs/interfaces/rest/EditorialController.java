package ir.haramyar.martyrs.interfaces.rest;

import ir.haramyar.kit.security.CurrentActor;
import ir.haramyar.kit.security.HaramyarSecurityProperties;
import ir.haramyar.martyrs.application.EditorialHandlers;
import ir.haramyar.martyrs.application.Ports;
import ir.haramyar.martyrs.application.ProfileViews.EditorialProfile;
import ir.haramyar.martyrs.domain.Values;
import ir.haramyar.martyrs.domain.Values.EditorialActor;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Editorial workspace. The Hive BFF admits each operation only with its {@code martyrs.editorial} action (edit,
 * submit, review, publish); this controller requires a staff account and acts in the editor or reviewer capacity the
 * operation belongs to. Responses are never cached.
 */
@RestController
@RequestMapping("/editorial")
class EditorialController {
  private final EditorialHandlers handlers;
  private final HaramyarSecurityProperties security;

  EditorialController(EditorialHandlers handlers, HaramyarSecurityProperties security) {
    this.handlers = handlers;
    this.security = security;
  }

  private EditorialActor editor() { return new EditorialActor(CurrentActor.requireStaff(security.staffRole()).userId(), EditorialActor.Role.EDITOR); }
  private EditorialActor reviewer() { return new EditorialActor(CurrentActor.requireStaff(security.staffRole()).userId(), EditorialActor.Role.REVIEWER); }

  @GetMapping("/profiles")
  Ports.Page<Ports.EditorialSummary> list(@RequestParam(required = false) List<Values.PublicationStatus> status, @RequestParam(required = false) String q,
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    editor();
    return handlers.list(status, q, page, size);
  }

  @PostMapping("/profiles")
  @ResponseStatus(HttpStatus.CREATED)
  EditorialProfile create(@Valid @RequestBody EditorialRequests.CreateProfile body) {
    return handlers.createDraft(editor(), new Values.MartyrSlug(body.slug()), body.content().toValue());
  }

  @GetMapping("/profiles/{id}")
  EditorialProfile get(@PathVariable UUID id) { return handlers.get(editor(), id); }

  @PutMapping("/profiles/{id}")
  EditorialProfile revise(@PathVariable UUID id, @Valid @RequestBody EditorialRequests.ReviseProfile body) {
    return handlers.revise(editor(), id, body.expectedVersion(), body.slug() == null ? null : new Values.MartyrSlug(body.slug()), body.content().toValue());
  }

  @PostMapping("/profiles/{id}/sources")
  EditorialProfile addSource(@PathVariable UUID id, @Valid @RequestBody EditorialRequests.SourceBody body) {
    return handlers.addSource(editor(), id, body.expectedVersion(), body.toValue());
  }

  @DeleteMapping("/profiles/{id}/sources/{sourceId}")
  EditorialProfile removeSource(@PathVariable UUID id, @PathVariable UUID sourceId, @RequestParam(required = false) Long expectedVersion) {
    return handlers.removeSource(editor(), id, expectedVersion, sourceId);
  }

  @PostMapping("/profiles/{id}/stories")
  EditorialProfile addStory(@PathVariable UUID id, @Valid @RequestBody EditorialRequests.StoryBody body) {
    return handlers.putStory(editor(), id, body.expectedVersion(), new Values.Story(UUID.randomUUID(), body.title(), body.body(), body.sourceIds()));
  }

  @PutMapping("/profiles/{id}/stories/{storyId}")
  EditorialProfile updateStory(@PathVariable UUID id, @PathVariable UUID storyId, @Valid @RequestBody EditorialRequests.StoryBody body) {
    return handlers.putStory(editor(), id, body.expectedVersion(), new Values.Story(storyId, body.title(), body.body(), body.sourceIds()));
  }

  @DeleteMapping("/profiles/{id}/stories/{storyId}")
  EditorialProfile removeStory(@PathVariable UUID id, @PathVariable UUID storyId, @RequestParam(required = false) Long expectedVersion) {
    return handlers.removeStory(editor(), id, expectedVersion, storyId);
  }

  @PostMapping(path = "/profiles/{id}/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  EditorialProfile upload(@PathVariable UUID id, @RequestPart("file") MultipartFile file, @RequestParam EditorialHandlers.MediaUsage usage,
      @RequestParam(required = false) String caption, @RequestParam(required = false) String credit, @RequestParam(required = false) Long expectedVersion) throws IOException {
    return handlers.uploadMedia(editor(), id, expectedVersion, file.getBytes(), usage, caption, credit);
  }

  @DeleteMapping("/profiles/{id}/media/{mediaId}")
  EditorialProfile removeMedia(@PathVariable UUID id, @PathVariable UUID mediaId, @RequestParam(required = false) Long expectedVersion) {
    return handlers.removeMedia(editor(), id, expectedVersion, mediaId);
  }

  @GetMapping("/media/{mediaId}")
  ResponseEntity<byte[]> preview(@PathVariable UUID mediaId) {
    editor();
    var media = handlers.previewMedia(mediaId);
    return ResponseEntity.ok().contentType(MediaType.parseMediaType(media.contentType())).cacheControl(CacheControl.noStore())
        .header("X-Content-Type-Options", "nosniff").body(media.bytes());
  }

  @PostMapping("/profiles/{id}/submit")
  EditorialProfile submit(@PathVariable UUID id, @Valid @RequestBody(required = false) EditorialRequests.Transition body) {
    return handlers.submitForReview(editor(), id, body == null ? null : body.expectedVersion());
  }

  @PostMapping("/profiles/{id}/request-changes")
  EditorialProfile requestChanges(@PathVariable UUID id, @Valid @RequestBody EditorialRequests.Transition body) {
    return handlers.requestChanges(reviewer(), id, body.expectedVersion(), body.note());
  }

  @PostMapping("/profiles/{id}/publish")
  EditorialProfile publish(@PathVariable UUID id, @Valid @RequestBody(required = false) EditorialRequests.Transition body) {
    return handlers.publish(reviewer(), id, body == null ? null : body.expectedVersion());
  }

  @PostMapping("/profiles/{id}/archive")
  EditorialProfile archive(@PathVariable UUID id, @Valid @RequestBody(required = false) EditorialRequests.Transition body) {
    return handlers.archive(reviewer(), id, body == null ? null : body.expectedVersion(), body == null ? null : body.note());
  }

  @PostMapping("/profiles/{id}/reopen")
  EditorialProfile reopen(@PathVariable UUID id, @Valid @RequestBody(required = false) EditorialRequests.Transition body) {
    return handlers.reopen(editor(), id, body == null ? null : body.expectedVersion());
  }
}
