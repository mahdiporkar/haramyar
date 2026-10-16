package ir.haramyar.shell.notification;

import io.hiveplatform.spring.HiveException;
import ir.haramyar.kit.security.CurrentActor;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in user's own notifications; ownership is enforced in every query. */
@RestController
class NotificationController {
  private final JdbcTemplate db;

  NotificationController(JdbcTemplate db) { this.db = db; }

  @GetMapping("/notifications")
  Map<String, Object> mine(@RequestParam(defaultValue = "false") boolean unreadOnly) {
    UUID user = CurrentActor.require().userId();
    List<Map<String, Object>> items = db.queryForList("""
        select id, kind, subject_id as "subjectId", reference, occurred_at as "occurredAt", read_at as "readAt"
        from notification where hive_user_id = ? and (? = false or read_at is null)
        order by occurred_at desc limit 50""", user, unreadOnly);
    Integer unread = db.queryForObject("select count(*) from notification where hive_user_id = ? and read_at is null", Integer.class, user);
    return Map.of("items", items, "unread", unread == null ? 0 : unread);
  }

  @PostMapping("/notifications/{id}/read")
  ResponseEntity<Void> read(@PathVariable UUID id) {
    UUID user = CurrentActor.require().userId();
    if (db.update("update notification set read_at = coalesce(read_at, now()) where id = ? and hive_user_id = ?", id, user) != 1)
      throw HiveException.notFound("Unknown notification");
    return ResponseEntity.noContent().build();
  }
}
