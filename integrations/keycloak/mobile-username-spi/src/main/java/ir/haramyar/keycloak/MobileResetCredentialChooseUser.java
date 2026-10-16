package ir.haramyar.keycloak;

import jakarta.ws.rs.core.MultivaluedMap;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.authenticators.resetcred.ResetCredentialChooseUser;

/**
 * "Forgot password" user lookup with phone-shaped input normalized to E.164. Keycloak's own behavior is unchanged:
 * the reset link goes only to a verified e-mail address and the response never reveals whether an account exists.
 * Phone-based recovery without proof of phone ownership is deliberately not offered.
 */
public class MobileResetCredentialChooseUser extends ResetCredentialChooseUser {
  public static final String ID = "haramyar-reset-credentials-choose-user";

  @Override
  public void action(AuthenticationFlowContext context) {
    MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
    String username = formData.getFirst("username");
    if (MobileNumbers.looksLikePhone(username)) MobileNumbers.normalize(username).ifPresent(normalized -> formData.putSingle("username", normalized));
    super.action(context);
  }

  @Override public String getId() { return ID; }
  @Override public String getDisplayType() { return "HaramYar choose user (mobile aware)"; }
  @Override public String getHelpText() { return "Choose a user to reset credentials for; mobile numbers are normalized to E.164."; }
}
