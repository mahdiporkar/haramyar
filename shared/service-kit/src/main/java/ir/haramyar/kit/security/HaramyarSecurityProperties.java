package ir.haramyar.kit.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Trust-boundary configuration of a HaramYar backend.
 *
 * @param issuer exact issuer of tokens forwarded by the Hive BFF (the Keycloak realm URL as browsers see it)
 * @param jwkSetUri where signing keys are fetched; may use an internal address while {@code issuer} stays public
 * @param clientId the BFF's OIDC client; forwarded tokens must have been issued to it ({@code azp})
 * @param staffRole realm role carried only by provisioned, MFA-enrolled staff accounts
 * @param publicPaths paths served to anonymous callers (Hive {@code PUBLIC}/{@code HYBRID} operations)
 * @param identityBinding whether the first (issuer, subject) to Hive user binding is pinned and later mismatches refused
 */
@ConfigurationProperties("haramyar.security")
public record HaramyarSecurityProperties(String issuer, String jwkSetUri, String clientId, String staffRole, List<String> publicPaths, Boolean identityBinding) {
  public HaramyarSecurityProperties {
    staffRole = staffRole == null || staffRole.isBlank() ? "haramyar-staff" : staffRole;
    publicPaths = publicPaths == null || publicPaths.isEmpty() ? List.of("/public/**", "/mfe/**") : List.copyOf(publicPaths);
    identityBinding = identityBinding == null || identityBinding;
  }
}
