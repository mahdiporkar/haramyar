package ir.haramyar.kit.security;

import io.hiveplatform.spring.HiveException;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;

/** Access to the verified caller of the current request. */
public final class CurrentActor {
  private CurrentActor() {}

  public static Optional<Actor> optional() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    return authentication instanceof ActorAuthentication actor ? Optional.of(actor.actor()) : Optional.empty();
  }

  public static Actor require() {
    return optional().orElseThrow(() -> new HiveException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "Sign in to continue"));
  }

  /**
   * Requires a staff account. Staff authentication (provisioned account, enforced MFA) is checked here independently of
   * the business permission the Hive BFF already verified for the route operation.
   */
  public static Actor requireStaff(String staffRole) {
    Actor actor = require();
    if (!actor.hasRealmRole(staffRole)) throw new HiveException(HttpStatus.FORBIDDEN, "STAFF_AUTHENTICATION_REQUIRED", "This operation requires a staff account");
    return actor;
  }
}
