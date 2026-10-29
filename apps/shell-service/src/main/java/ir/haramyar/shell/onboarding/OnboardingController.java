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

  /**
   * Always asks Hive (idempotent: an active assignment is reported as already assigned), so a grant that is missing in
   * Hive is restored even when this service has already recorded the user. {@code onboarded_user} is an audit record only.
   */
  @PostMapping("/onboarding")
  Map<String, Object> onboard() {
    Actor actor = CurrentActor.require();
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
