package ir.haramyar.shell.occasion;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OccasionRepository {
  private static final TypeReference<Map<String, String>> TEXTS = new TypeReference<>() {};
  private final JdbcTemplate db;
  private final ObjectMapper json;

  public OccasionRepository(JdbcTemplate db, ObjectMapper json) {
    this.db = db;
    this.json = json;
  }

  public List<Occasion> all() {
    return db.query("select * from occasion order by status, priority desc, starts_at desc", this::map);
  }

  public List<Occasion> scheduled() {
    return db.query("select * from occasion where status = 'SCHEDULED'", this::map);
  }

  public Optional<Occasion> find(UUID id) {
    return db.query("select * from occasion where id = ?", this::map, id).stream().findFirst();
  }

  public void add(Occasion o) {
    db.update("insert into occasion(id, occasion_key, theme, titles, subtitles, starts_at, ends_at, priority, status, version) values (?, ?, ?, cast(? as jsonb), cast(? as jsonb), ?, ?, ?, ?, 0)",
        o.id(), o.key(), o.theme().name(), write(o.titles()), write(o.subtitles()), Timestamp.from(o.startsAt()), Timestamp.from(o.endsAt()), o.priority(), o.status().name());
  }

  /** @return false when the occasion changed concurrently (optimistic lock) */
  public boolean update(Occasion o) {
    return db.update("update occasion set theme = ?, titles = cast(? as jsonb), subtitles = cast(? as jsonb), starts_at = ?, ends_at = ?, priority = ?, status = ?, version = version + 1, updated_at = now() where id = ? and version = ?",
        o.theme().name(), write(o.titles()), write(o.subtitles()), Timestamp.from(o.startsAt()), Timestamp.from(o.endsAt()), o.priority(), o.status().name(), o.id(), o.version()) == 1;
  }

  private Occasion map(ResultSet r, int n) throws SQLException {
    try {
      return Occasion.reconstitute(r.getObject("id", UUID.class), r.getString("occasion_key"), Occasion.Theme.valueOf(r.getString("theme")),
          json.readValue(r.getString("titles"), TEXTS), json.readValue(r.getString("subtitles"), TEXTS), r.getTimestamp("starts_at").toInstant(),
          r.getTimestamp("ends_at").toInstant(), r.getInt("priority"), Occasion.Status.valueOf(r.getString("status")), r.getLong("version"));
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new SQLException(e);
    }
  }

  private String write(Map<String, String> texts) {
    try {
      return json.writeValueAsString(texts);
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }
}
