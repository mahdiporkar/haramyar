package ir.haramyar.keycloak;

import jakarta.ws.rs.core.MultivaluedMap;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.authenticators.browser.UsernamePasswordForm;
import org.keycloak.authentication.authenticators.browser.UsernamePasswordFormFactory;
import org.keycloak.models.KeycloakSession;

/**
 * The standard username/password form, with phone-shaped usernames normalized to E.164 before Keycloak looks the user
 * up. Password verification, brute-force protection and error messages stay Keycloak's own (no enumeration leaks).
 */
public class MobileUsernamePasswordForm extends UsernamePasswordForm {
  @Override
  protected boolean validateForm(AuthenticationFlowContext context, MultivaluedMap<String, String> formData) {
    String username = formData.getFirst("username");
    if (MobileNumbers.looksLikePhone(username)) MobileNumbers.normalize(username).ifPresent(normalized -> formData.putSingle("username", normalized));
    return super.validateForm(context, formData);
  }

  public static class Factory extends UsernamePasswordFormFactory {
    public static final String ID = "haramyar-mobile-username-password-form";
    private static final MobileUsernamePasswordForm SINGLETON = new MobileUsernamePasswordForm();

    @Override public String getId() { return ID; }
    @Override public String getDisplayType() { return "HaramYar mobile username/password form"; }
    @Override public String getHelpText() { return "Username/password form that normalizes Iranian and Iraqi mobile numbers to E.164 before lookup."; }
    @Override public Authenticator create(KeycloakSession session) { return SINGLETON; }
  }
}
