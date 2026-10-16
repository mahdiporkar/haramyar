package ir.haramyar.kit.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** Security-context representation of a verified {@link Actor}; realm roles become {@code REALM_<role>} authorities. */
public final class ActorAuthentication extends AbstractAuthenticationToken {
  private final Actor actor;

  public ActorAuthentication(Actor actor) {
    super(actor.realmRoles().stream().map(role -> new SimpleGrantedAuthority("REALM_" + role)).toList());
    this.actor = actor;
    setAuthenticated(true);
  }

  public Actor actor() { return actor; }

  @Override public Object getCredentials() { return null; }
  @Override public Object getPrincipal() { return actor; }
  @Override public String getName() { return actor.userId().toString(); }
}
