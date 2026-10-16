package ir.haramyar.martyrs.application;

import ir.haramyar.martyrs.domain.EditorialRuleViolation;
import ir.haramyar.martyrs.domain.Text;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Three independent editions per work; publication requires a reviewed PDF in private MinIO. */
@Service
public class BookLibrary {
  public static final long MAX_PDF_BYTES = 30L * 1024 * 1024;
  private final JdbcTemplate db;
  private final BookObjectStore storage;

  public BookLibrary(JdbcTemplate db, BookObjectStore storage) {
    this.db = db;
    this.storage = storage;
  }

  public record Edition(UUID id, String locale, String title, String summary, String status,
      boolean hasFile, long version, UUID submittedBy) {}
  public record Book(UUID id, String kind, String originalTitle, String sourceUrl, List<Edition> editions) {}
  public record PublicEdition(UUID id, String locale, String title, String summary) {}
  public record PublicBook(UUID id, String kind, String originalTitle, String sourceUrl,
      PublicEdition selectedEdition, List<PublicEdition> availableEditions) {}
  public record FileLink(String key, String title) {}

  @Transactional(readOnly = true)
  public List<Book> editorial(UUID profileId) {
    requireProfile(profileId);
    return db.query("select id,kind,original_title,source_url from martyr_book where profile_id=? order by kind,created_at,id",
        (rs, n) -> new Book((UUID) rs.getObject(1), rs.getString(2), rs.getString(3), rs.getString(4),
            editions((UUID) rs.getObject(1))), profileId);
  }

  @Transactional
  public Book create(UUID profileId, String kind, String originalTitle, String sourceUrl) {
    requireProfile(profileId);
    if (!List.of("AUTHORED", "ABOUT").contains(kind)) throw EditorialRuleViolation.invalid("Invalid work type");
    String title = Text.required(originalTitle, "Original book title", 2, 240);
    String source = httpsUrl(sourceUrl);
    UUID id = UUID.randomUUID();
    db.update("insert into martyr_book(id,profile_id,kind,original_title,source_url) values(?,?,?,?,?)",
        id, profileId, kind, title, source);
    return new Book(id, kind, title, source, List.of());
  }

  @Transactional
  public List<Book> saveEdition(UUID bookId, String locale, String title, String summary, Long expectedVersion) {
    language(locale);
    String cleanedTitle = Text.required(title, "Edition title", 2, 240);
    String cleanedSummary = Text.optional(summary, "Edition summary", 4000);
    UUID profileId = owner(bookId);
    var current = editions(bookId).stream().filter(e -> e.locale().equals(locale)).findFirst();
    if (current.isEmpty()) {
      if (expectedVersion != null) throw new Ports.ConcurrentModification();
      db.update("insert into martyr_book_edition(id,book_id,locale,title,summary,status) values(?,?,?,?,?,'DRAFT')",
          UUID.randomUUID(), bookId, locale, cleanedTitle, cleanedSummary);
    } else {
      if (!"DRAFT".equals(current.get().status())) throw notEditable();
      if (expectedVersion == null || db.update("""
          update martyr_book_edition set title=?,summary=?,version=version+1
          where book_id=? and locale=? and version=? and status='DRAFT'
          """, cleanedTitle, cleanedSummary, bookId, locale, expectedVersion) != 1) throw new Ports.ConcurrentModification();
    }
    return editorial(profileId);
  }

  /** Spool to a restricted temporary file so S3 retries can reopen it without loading 30 MiB on the heap. */
  public void upload(UUID editionId, long expectedVersion, MultipartFile file) throws IOException {
    var edition = edition(editionId);
    if (!"DRAFT".equals(edition.status())) throw notEditable();
    long size = file.getSize();
    if (size < 5 || size > MAX_PDF_BYTES) throw EditorialRuleViolation.invalid("PDF must be 5 bytes to 30 MiB");
    String key = "books/" + editionId + "/" + UUID.randomUUID() + ".pdf";
    Path temporary = Files.createTempFile("haramyar-book-", ".pdf");
    try {
      var digest = sha256();
      try (DigestInputStream content = new DigestInputStream(file.getInputStream(), digest);
           var target = Files.newOutputStream(temporary)) {
        byte[] magic = content.readNBytes(5);
        if (magic.length != 5 || !java.util.Arrays.equals(magic, "%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII)))
          throw EditorialRuleViolation.invalid("Only PDF documents are allowed");
        target.write(magic);
        content.transferTo(target);
      }
      if (Files.size(temporary) != size) throw EditorialRuleViolation.invalid("PDF size mismatch");
      storage.put(key, temporary);
      String sha = java.util.HexFormat.of().formatHex(digest.digest());
      if (db.update("""
          update martyr_book_edition set storage_key=?,sha256=?,size_bytes=?,version=version+1
          where id=? and status='DRAFT' and version=?
          """, key, sha, size, editionId, expectedVersion) != 1) throw new Ports.ConcurrentModification();
    } finally {
      Files.deleteIfExists(temporary);
    }
    // A stale concurrent update can leave an unreachable object; keys are opaque and private.
  }

  @Transactional
  public void transition(UUID editionId, long expectedVersion, UUID actor, boolean publish, boolean fourEyes) {
    var edition = edition(editionId);
    String from = publish ? "IN_REVIEW" : "DRAFT";
    if (!from.equals(edition.status())) throw new EditorialRuleViolation(EditorialRuleViolation.Code.INVALID_TRANSITION, "Edition status has changed");
    if (!edition.hasFile()) throw new EditorialRuleViolation(EditorialRuleViolation.Code.NOT_READY_FOR_REVIEW, "A complete PDF is required");
    if (publish && fourEyes && actor.equals(edition.submittedBy()))
      throw new EditorialRuleViolation(EditorialRuleViolation.Code.FOUR_EYES_REQUIRED, "Another editor must approve this edition");
    int changed = publish ? db.update("""
        update martyr_book_edition set status='PUBLISHED',published_by=?,published_at=?,version=version+1
        where id=? and status='IN_REVIEW' and version=?
        """, actor, java.sql.Timestamp.from(Instant.now()), editionId, expectedVersion)
        : db.update("""
        update martyr_book_edition set status='IN_REVIEW',submitted_by=?,version=version+1
        where id=? and status='DRAFT' and version=?
        """, actor, editionId, expectedVersion);
    if (changed != 1) throw new Ports.ConcurrentModification();
  }

  @Transactional(readOnly = true)
  public List<PublicBook> published(String slug, String locale) {
    language(locale);
    var rows = db.query("""
        select b.id,b.kind,b.original_title,b.source_url,e.id,e.locale,e.title,e.summary
        from martyr_book b
        join martyr_profile p on p.id=b.profile_id and p.status='PUBLISHED'
        join martyr_book_edition e on e.book_id=b.id and e.status='PUBLISHED' and e.storage_key is not null
        where p.slug=? order by b.kind,b.created_at,b.id,e.locale
        """, (rs, n) -> new PublishedRow((UUID) rs.getObject(1), rs.getString(2), rs.getString(3), rs.getString(4),
        new PublicEdition((UUID) rs.getObject(5), rs.getString(6), rs.getString(7), rs.getString(8))), slug);
    Map<UUID, List<PublishedRow>> groups = new LinkedHashMap<>();
    rows.forEach(row -> groups.computeIfAbsent(row.bookId(), unused -> new ArrayList<>()).add(row));
    return groups.values().stream().map(group -> {
      var first = group.get(0);
      var editions = group.stream().map(PublishedRow::edition).toList();
      var selected = editions.stream().filter(e -> e.locale().equals(locale)).findFirst()
          .or(() -> editions.stream().filter(e -> e.locale().equals("ar")).findFirst()).orElse(editions.get(0));
      return new PublicBook(first.bookId(), first.kind(), first.title(), first.sourceUrl(), selected, editions);
    }).toList();
  }

  @Transactional(readOnly = true)
  public FileLink publishedFile(UUID editionId) {
    return db.query("""
        select e.storage_key,e.title from martyr_book_edition e
        join martyr_book b on b.id=e.book_id
        join martyr_profile p on p.id=b.profile_id and p.status='PUBLISHED'
        where e.id=? and e.status='PUBLISHED' and e.storage_key is not null
        """, (rs, n) -> new FileLink(rs.getString(1), rs.getString(2)), editionId).stream().findFirst()
        .orElseThrow(BookLibrary::missing);
  }

  public InputStream open(FileLink file) { return storage.open(file.key()); }

  private List<Edition> editions(UUID bookId) {
    return db.query("""
        select id,locale,title,summary,status,storage_key,version,submitted_by
        from martyr_book_edition where book_id=? order by locale
        """, (rs, n) -> new Edition((UUID) rs.getObject(1), rs.getString(2), rs.getString(3), rs.getString(4),
        rs.getString(5), rs.getString(6) != null, rs.getLong(7), (UUID) rs.getObject(8)), bookId);
  }

  private Edition edition(UUID editionId) {
    return db.query("""
        select id,locale,title,summary,status,storage_key,version,submitted_by
        from martyr_book_edition where id=?
        """, (rs, n) -> new Edition((UUID) rs.getObject(1), rs.getString(2), rs.getString(3), rs.getString(4),
        rs.getString(5), rs.getString(6) != null, rs.getLong(7), (UUID) rs.getObject(8)), editionId)
        .stream().findFirst().orElseThrow(BookLibrary::missing);
  }

  private UUID owner(UUID bookId) {
    return db.query("select profile_id from martyr_book where id=?", (rs, n) -> (UUID) rs.getObject(1), bookId)
        .stream().findFirst().orElseThrow(BookLibrary::missing);
  }
  private void requireProfile(UUID id) {
    if (db.queryForObject("select count(*) from martyr_profile where id=?", Integer.class, id) == 0) throw missing();
  }
  private static void language(String locale) {
    if (locale == null || !List.of("ar", "fa", "en").contains(locale)) throw EditorialRuleViolation.invalid("Unsupported language");
  }
  private static String httpsUrl(String value) {
    try {
      var url = java.net.URI.create(Text.required(value, "Source URL", 10, 1000));
      if (!"https".equals(url.getScheme()) || url.getHost() == null || url.getUserInfo() != null) throw EditorialRuleViolation.invalid("HTTPS source required");
      return url.toString();
    } catch (IllegalArgumentException invalid) { throw EditorialRuleViolation.invalid("Invalid source URL"); }
  }
  private static MessageDigest sha256() {
    try { return MessageDigest.getInstance("SHA-256"); }
    catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
  }
  private static EditorialRuleViolation missing() { return new EditorialRuleViolation(EditorialRuleViolation.Code.NOT_FOUND, "Book not found"); }
  private static EditorialRuleViolation notEditable() { return new EditorialRuleViolation(EditorialRuleViolation.Code.NOT_EDITABLE, "Only draft editions can be changed"); }
  private record PublishedRow(UUID bookId, String kind, String title, String sourceUrl, PublicEdition edition) {}
}
