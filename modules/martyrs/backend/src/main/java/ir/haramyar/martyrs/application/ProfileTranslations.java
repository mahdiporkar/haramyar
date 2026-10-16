package ir.haramyar.martyrs.application;

import ir.haramyar.martyrs.domain.EditorialRuleViolation;
import ir.haramyar.martyrs.domain.Text;
import ir.haramyar.martyrs.domain.Values.SeoMetadata;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Separate, four-eyes-approved translations of the profile itself (not of books or stories). */
@Service
public class ProfileTranslations {
  private final JdbcTemplate db;

  public ProfileTranslations(JdbcTemplate db) { this.db = db; }

  public record Translation(String locale, String fullName, String honorific, String shortDescription,
      String biography, String seoTitle, String seoDescription, String status, long version, UUID submittedBy) {}

  @Transactional(readOnly = true)
  public List<Translation> editorial(UUID profileId) {
    sourceLocale(profileId);
    return db.query("""
        select locale,full_name,honorific,short_description,biography,seo_title,seo_description,status,version,submitted_by
        from martyr_profile_translation where profile_id=? order by locale
        """, (rs, n) -> new Translation(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
        rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8), rs.getLong(9), (UUID) rs.getObject(10)), profileId);
  }

  @Transactional
  public List<Translation> save(UUID profileId, String locale, String fullName, String honorific,
      String shortDescription, String biography, String seoTitle, String seoDescription, Long expectedVersion) {
    language(locale);
    if (locale.equals(sourceLocale(profileId))) throw EditorialRuleViolation.invalid("Source language is edited on the original profile");
    String cleanedName = Text.required(fullName, "Translated name", 2, 160);
    String cleanedHonorific = Text.optional(honorific, "Translated honorific", 80);
    String cleanedDescription = Text.required(shortDescription, "Translated summary", 2, 300);
    String cleanedBiography = Text.required(biography, "Translated biography", 20, 50_000);
    String cleanedSeoTitle = Text.optional(seoTitle, "Translated SEO title", 70);
    String cleanedSeoDescription = Text.optional(seoDescription, "Translated SEO description", 170);
    var current = editorial(profileId).stream().filter(t -> t.locale().equals(locale)).findFirst();
    if (current.isEmpty()) {
      if (expectedVersion != null) throw new Ports.ConcurrentModification();
      db.update("""
          insert into martyr_profile_translation(profile_id,locale,full_name,honorific,short_description,biography,seo_title,seo_description)
          values(?,?,?,?,?,?,?,?)
          """, profileId, locale, cleanedName, cleanedHonorific, cleanedDescription, cleanedBiography, cleanedSeoTitle, cleanedSeoDescription);
    } else {
      if (!"DRAFT".equals(current.get().status())) throw notEditable();
      if (expectedVersion == null || db.update("""
          update martyr_profile_translation set full_name=?,honorific=?,short_description=?,biography=?,seo_title=?,seo_description=?,version=version+1
          where profile_id=? and locale=? and version=? and status='DRAFT'
          """, cleanedName, cleanedHonorific, cleanedDescription, cleanedBiography, cleanedSeoTitle, cleanedSeoDescription,
          profileId, locale, expectedVersion) != 1) throw new Ports.ConcurrentModification();
    }
    return editorial(profileId);
  }

  @Transactional
  public void transition(UUID profileId, String locale, long expectedVersion, UUID actor, boolean publish, boolean fourEyes) {
    language(locale);
    var current = editorial(profileId).stream().filter(t -> t.locale().equals(locale)).findFirst()
        .orElseThrow(ProfileTranslations::missing);
    String from = publish ? "IN_REVIEW" : "DRAFT";
    if (!from.equals(current.status()))
      throw new EditorialRuleViolation(EditorialRuleViolation.Code.INVALID_TRANSITION, "Translation status has changed");
    if (publish && fourEyes && actor.equals(current.submittedBy()))
      throw new EditorialRuleViolation(EditorialRuleViolation.Code.FOUR_EYES_REQUIRED, "Another editor must approve this translation");
    int changed = publish ? db.update("""
        update martyr_profile_translation set status='PUBLISHED',published_by=?,published_at=?,version=version+1
        where profile_id=? and locale=? and version=? and status='IN_REVIEW'
        """, actor, Timestamp.from(Instant.now()), profileId, locale, expectedVersion)
        : db.update("""
        update martyr_profile_translation set status='IN_REVIEW',submitted_by=?,version=version+1
        where profile_id=? and locale=? and version=? and status='DRAFT'
        """, actor, profileId, locale, expectedVersion);
    if (changed != 1) throw new Ports.ConcurrentModification();
  }

  @Transactional(readOnly = true)
  public ProfileViews.PublicProfile published(ProfileViews.PublicProfile original, String locale) {
    if (locale == null || locale.equals(original.contentLocale())) return original;
    language(locale);
    return db.query("""
        select t.full_name,t.honorific,t.short_description,t.biography,t.seo_title,t.seo_description
        from martyr_profile_translation t join martyr_profile p on p.id=t.profile_id
        where p.slug=? and p.status='PUBLISHED' and t.locale=? and t.status='PUBLISHED'
        """, (rs, n) -> new ProfileViews.PublicProfile(original.slug(), rs.getString(1), rs.getString(2), locale, original.sourceLocale(),
        rs.getString(3), rs.getString(4), original.birth(), original.martyrdom(), original.portrait(),
        original.gallery(), original.sources(), original.stories(), new SeoMetadata(rs.getString(5), rs.getString(6)),
        original.firstPublishedAt(), original.updatedAt()), original.slug(), locale)
        .stream().findFirst().orElse(original);
  }

  private String sourceLocale(UUID profileId) {
    return db.query("select content_locale from martyr_profile where id=?", (rs, n) -> rs.getString(1), profileId)
        .stream().findFirst().orElseThrow(ProfileTranslations::missing);
  }

  private static void language(String locale) {
    if (locale == null || !List.of("fa", "ar", "en").contains(locale)) throw EditorialRuleViolation.invalid("Unsupported language");
  }
  private static EditorialRuleViolation missing() { return new EditorialRuleViolation(EditorialRuleViolation.Code.NOT_FOUND, "Translation not found"); }
  private static EditorialRuleViolation notEditable() { return new EditorialRuleViolation(EditorialRuleViolation.Code.NOT_EDITABLE, "Only draft translations can be changed"); }
}
