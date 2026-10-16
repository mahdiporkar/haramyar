package ir.haramyar.keycloak;

import jakarta.ws.rs.core.MultivaluedMap;
import java.util.List;
import org.keycloak.Config;
import org.keycloak.authentication.FormAction;
import org.keycloak.authentication.FormActionFactory;
import org.keycloak.authentication.FormContext;
import org.keycloak.authentication.ValidationContext;
import org.keycloak.events.Errors;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.FormMessage;
import org.keycloak.provider.ProviderConfigProperty;

/**
 * Registration step placed before "Registration User Profile Creation": the mobile number entered as username must be
 * a valid Iranian or Iraqi mobile number and is rewritten to E.164, so Keycloak's unique-username check rejects every
 * other spelling of an existing number. The number is not marked verified (verification is a separate process).
 */
public class MobileUsernameRegistrationAction implements FormAction, FormActionFactory {
  public static final String ID = "haramyar-mobile-registration";
  public static final String ERROR_MESSAGE = "haramyarInvalidMobile";

  @Override
  public void validate(ValidationContext context) {
    MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
    var normalized = MobileNumbers.normalize(formData.getFirst("username"));
    if (normalized.isEmpty()) {
      context.error(Errors.INVALID_REGISTRATION);
      context.validationError(formData, List.of(new FormMessage("username", ERROR_MESSAGE)));
      return;
    }
    formData.putSingle("username", normalized.get());
    context.success();
  }

  @Override public void buildPage(FormContext context, LoginFormsProvider form) { }
  @Override public void success(FormContext context) { }
  @Override public boolean requiresUser() { return false; }
  @Override public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) { return true; }
  @Override public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) { }
  @Override public void close() { }

  @Override public String getId() { return ID; }
  @Override public String getDisplayType() { return "HaramYar mobile username normalization"; }
  @Override public String getHelpText() { return "Validates the mobile number used as username and normalizes it to E.164."; }
  @Override public String getReferenceCategory() { return null; }
  @Override public boolean isConfigurable() { return false; }
  @Override public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
    return new AuthenticationExecutionModel.Requirement[] {AuthenticationExecutionModel.Requirement.REQUIRED, AuthenticationExecutionModel.Requirement.DISABLED};
  }
  @Override public boolean isUserSetupAllowed() { return false; }
  @Override public List<ProviderConfigProperty> getConfigProperties() { return List.of(); }
  @Override public FormAction create(KeycloakSession session) { return this; }
  @Override public void init(Config.Scope config) { }
  @Override public void postInit(KeycloakSessionFactory factory) { }
}
