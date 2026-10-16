package ir.haramyar.martyrs.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** JPA representations; embedded value collections are stored as JSON documents of the aggregate's own row. */
final class Entities {
  private Entities() {}

  @Entity(name = "ProfileEntity")
  @Table(name = "martyr_profile")
  static class ProfileEntity {
    @Id UUID id;
    @Column(nullable = false) String slug;
    @Column(nullable = false) String status;
    @Column(name = "full_name", nullable = false) String fullName;
    String honorific;
    @Column(name = "content_locale", nullable = false) String contentLocale;
    @Column(name = "short_description") String shortDescription;
    String biography;
    @JdbcTypeCode(SqlTypes.JSON) String birth;
    @JdbcTypeCode(SqlTypes.JSON) String martyrdom;
    @Column(name = "seo_title") String seoTitle;
    @Column(name = "seo_description") String seoDescription;
    @JdbcTypeCode(SqlTypes.JSON) String portrait;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false) String gallery;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false) String sources;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false) String stories;
    @Column(name = "created_by", nullable = false, updatable = false) UUID createdBy;
    @Column(name = "submitted_by") UUID submittedBy;
    @Column(name = "created_at", nullable = false, updatable = false) Instant createdAt;
    @Column(name = "updated_at", nullable = false) Instant updatedAt;
    @Column(name = "first_published_at") Instant firstPublishedAt;
    @Column(name = "published_at") Instant publishedAt;
    @Column(name = "search_text", nullable = false) String searchText;
    @Version long version;
  }

  @Entity(name = "HistoryEntity")
  @Immutable
  @Table(name = "martyr_editorial_history")
  static class HistoryEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(name = "profile_id", nullable = false) UUID profileId;
    @Column(name = "sequence_no", nullable = false) int sequenceNo;
    @Column(nullable = false) String operation;
    @Column(name = "from_status") String fromStatus;
    @Column(name = "to_status", nullable = false) String toStatus;
    @Column(name = "actor_id", nullable = false) UUID actorId;
    @Column(name = "actor_role", nullable = false) String actorRole;
    String note;
    @Column(name = "occurred_at", nullable = false) Instant occurredAt;
  }

  @Entity(name = "MediaEntity")
  @Immutable
  @Table(name = "media_object")
  static class MediaEntity {
    @Id UUID id;
    @Column(name = "profile_id", nullable = false) UUID profileId;
    @Column(name = "content_type", nullable = false) String contentType;
    @Column(name = "size_bytes", nullable = false) long sizeBytes;
    @Column(nullable = false) String sha256;
    @Column(name = "storage_key", nullable = false) String storageKey;
    @Column(name = "uploaded_by", nullable = false) UUID uploadedBy;
    @Column(name = "uploaded_at", nullable = false) Instant uploadedAt;
  }
}
