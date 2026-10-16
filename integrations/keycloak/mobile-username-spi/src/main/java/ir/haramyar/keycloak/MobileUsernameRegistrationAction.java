package ir.haramyar.keycloak;

import jakarta.ws.rs.core.MultivaluedMap;
import java.util.List;
import org.keycloak.authentication.FormContext;
import org.keycloak.authentication.ValidationContext;
import org.keycloak.authentication.forms.RegistrationUserCreation;
import org.keycloak.events.Errors;
import org.keycloak.http.HttpRequest;
import org.keycloak.models.utils.FormMessage;

/**
 * Replaces "Registration User Profile Creation" in the HaramYar registration flow: the mobile number entered as
 * username must be a valid Iranian or Iraqi mobile number and Keycloak validates and creates the user with its E.164
 * form, so its unique-username check rejects every other spelling of an existing number. All other behavior (user
 * profile validation, events, duplicate detection) is Keycloak's own. The number is not marked verified.
 */
public class MobileUsernameRegistrationAction extends RegistrationUserCreation {
  public static final String ID = "haramyar-registration-user-creation";
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
    super.validate(NormalizedRequests.context(ValidationContext.class, context, normalizedRequest(context.getHttpRequest(), normalized.get())));
  }

  @Override
  public void success(FormContext context) {
    var normalized = MobileNumbers.normalize(context.getHttpRequest().getDecodedFormParameters().getFirst("username"));
    super.success(normalized.isEmpty() ? context : NormalizedRequests.context(FormContext.class, context, normalizedRequest(context.getHttpRequest(), normalized.get())));
  }

  private static HttpRequest normalizedRequest(HttpRequest request, String username) {
    return NormalizedRequests.request(request, NormalizedRequests.withUsername(request.getDecodedFormParameters(), username));
  }

  @Override public String getId() { return ID; }
  @Override public String getDisplayType() { return "HaramYar registration user creation (mobile username)"; }
  @Override public String getHelpText() { return "Validates the mobile number used as username, normalizes it to E.164 and creates the user."; }
}
