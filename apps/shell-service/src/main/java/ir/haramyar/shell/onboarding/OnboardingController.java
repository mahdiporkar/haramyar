package ir.haramyar.shell.onboarding;

import io.hiveplatform.spring.HiveException;
import ir.haramyar.kit.security.Actor;
import ir.haramyar.kit.security.CurrentActor;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Secure, idempotent onboarding of a newly authenticated user into HaramYar (Hive {@code HYBRID} operation, so it is
 * reachable before the user holds any business permission). The acting user comes only from the secured request; the
 * role is fixed by configuration; nothing from user-controlled registration data influences what is granted.
 */
@RestController
class OnboardingController {
  private final HiveAccessClient hive;
  private final JdbcTemplate db;

  OnboardingController(HiveAccessClient hive, JdbcTemplate db) {
    this.hive = hive;
    this.db = db;
  }

  @PostMapping("/onboarding")
  Map<String, Object> onboard() {
    Actor actor = CurrentActor.require();
    boolean known = Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from onboarded_user where hive_user_id = ?)", Boolean.class, actor.userId()));
    if (known) return Map.of("status", "ALREADY_ONBOARDED", "role", hive.defaultRole());
    HiveAccessClient.Outcome outcome;
    try {
      outcome = hive.assignDefaultRole(actor.userId());
    } catch (IllegalStateException unavailable) {
      throw new HiveException(HttpStatus.SERVICE_UNAVAILABLE, "ONBOARDING_UNAVAILABLE", "Account setup is temporarily unavailable; please retry");
    }
    db.update("insert into onboarded_user(hive_user_id, role_key) values (?, ?) on conflict do nothing", actor.userId(), hive.defaultRole());
    return Map.of("status", outcome == HiveAccessClient.Outcome.ASSIGNED ? "ONBOARDED" : "ALREADY_ONBOARDED", "role", hive.defaultRole());
  }
}
