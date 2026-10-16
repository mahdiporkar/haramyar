package ir.haramyar.martyrs.interfaces.rest;

import ir.haramyar.kit.security.CurrentActor;
import ir.haramyar.kit.security.HaramyarSecurityProperties;
import ir.haramyar.martyrs.application.BookLibrary;
import ir.haramyar.martyrs.domain.EditorialPolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
class BooksController {
  private final BookLibrary library;
  private final HaramyarSecurityProperties security;
  private final EditorialPolicy policy;

  BooksController(BookLibrary library, HaramyarSecurityProperties security, EditorialPolicy policy) {
    this.library = library;
    this.security = security;
    this.policy = policy;
  }

  record NewBook(@NotBlank String kind, @NotBlank String originalTitle, @NotBlank String sourceUrl) {}
  record EditionBody(@NotBlank String title, String summary, Long expectedVersion) {}
  record VersionBody(@NotNull Long expectedVersion) {}

  @GetMapping("/public/profiles/{slug}/books")
  List<BookLibrary.PublicBook> publicBooks(@PathVariable String slug, @RequestParam(defaultValue = "ar") String locale) {
    return library.published(slug, locale);
  }

  @GetMapping("/public/books/{editionId}/file")
  ResponseEntity<StreamingResponseBody> publicFile(@PathVariable UUID editionId) {
    var link = library.publishedFile(editionId);
    StreamingResponseBody body = out -> {
      try (var file = library.open(link)) { file.transferTo(out); }
    };
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .contentType(MediaType.APPLICATION_PDF)
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=book-" + editionId + ".pdf")
        .header("X-Content-Type-Options", "nosniff")
        .header("Content-Security-Policy", "sandbox")
        .body(body);
  }

  @GetMapping("/editorial/profiles/{profileId}/books")
  List<BookLibrary.Book> editorial(@PathVariable UUID profileId) {
    staff();
    return library.editorial(profileId);
  }

  @PostMapping("/editorial/profiles/{profileId}/books")
  @ResponseStatus(HttpStatus.CREATED)
  BookLibrary.Book create(@PathVariable UUID profileId, @Valid @RequestBody NewBook input) {
    staff();
    return library.create(profileId, input.kind(), input.originalTitle(), input.sourceUrl());
  }

  @PutMapping("/editorial/books/{bookId}/editions/{locale}")
  List<BookLibrary.Book> upsert(@PathVariable UUID bookId, @PathVariable String locale, @Valid @RequestBody EditionBody body) {
    staff();
    return library.saveEdition(bookId, locale, body.title(), body.summary(), body.expectedVersion());
  }

  @PostMapping(path = "/editorial/books/editions/{editionId}/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void upload(@PathVariable UUID editionId, @RequestPart("file") MultipartFile file,
      @RequestParam long expectedVersion) throws IOException {
    staff();
    library.upload(editionId, expectedVersion, file);
  }

  @PostMapping("/editorial/books/editions/{editionId}/submit")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void submit(@PathVariable UUID editionId, @Valid @RequestBody VersionBody body) {
    library.transition(editionId, body.expectedVersion(), staff(), false, policy.fourEyes());
  }

  @PostMapping("/editorial/books/editions/{editionId}/publish")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void publish(@PathVariable UUID editionId, @Valid @RequestBody VersionBody body) {
    library.transition(editionId, body.expectedVersion(), staff(), true, policy.fourEyes());
  }

  private UUID staff() { return CurrentActor.requireStaff(security.staffRole()).userId(); }
}
