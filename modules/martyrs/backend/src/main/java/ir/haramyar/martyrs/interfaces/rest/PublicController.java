package ir.haramyar.martyrs.interfaces.rest;

import ir.haramyar.martyrs.application.Ports;
import ir.haramyar.martyrs.application.ProfileViews;
import ir.haramyar.martyrs.application.PublicQueries;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Anonymous API (Hive PUBLIC operations). Only published content is reachable; everything else is a uniform 404. */
@RestController
@RequestMapping("/public")
class PublicController {
  private final PublicQueries queries;

  PublicController(PublicQueries queries) { this.queries = queries; }

  @GetMapping("/profiles")
  ResponseEntity<Ports.Page<Ports.PublicProfileSummary>> search(@RequestParam(required = false) String q, @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic()).body(queries.search(q, page, size));
  }

  @GetMapping("/profiles/{slug}")
  ResponseEntity<ProfileViews.PublicProfile> profile(@PathVariable String slug) {
    return queries.bySlug(slug)
        .map(p -> ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic()).body(p))
        .orElseGet(() -> ResponseEntity.status(404).cacheControl(CacheControl.noStore()).build());
  }

  @GetMapping("/sitemap")
  List<Ports.SitemapEntry> sitemap() {
    return queries.sitemap();
  }

  @GetMapping("/media/{mediaId}")
  ResponseEntity<byte[]> media(@PathVariable UUID mediaId) {
    return queries.media(mediaId)
        .map(m -> ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(m.contentType()))
            .cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePublic())
            .eTag("\"" + m.sha256() + "\"")
            .header("X-Content-Type-Options", "nosniff")
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
            .body(m.bytes()))
        .orElseGet(() -> ResponseEntity.status(404).cacheControl(CacheControl.noStore()).build());
  }
}
