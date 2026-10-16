package ir.haramyar.shell.onboarding;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.hiveplatform.spring.CorrelationId;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * Anti-corruption layer over the Hive control-plane admin API. It exposes exactly one capability — assigning the
 * configured default business role to a canonical user — so the deployment's provisioning credential cannot be used
 * for anything else through this service. The credential never leaves the server and is never logged.
 */
public class HiveAccessClient {
  public enum Outcome { ASSIGNED, ALREADY_ASSIGNED }

  private final URI authorizationUrl;
  private final String authorizationHeader;
  private final String defaultRole;
  private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();
  private final ObjectMapper json;

  public HiveAccessClient(String authorizationUrl, String provisioningPassword, String defaultRole, ObjectMapper json) {
    if (provisioningPassword == null || provisioningPassword.length() < 32) throw new IllegalStateException("A Hive provisioning credential of at least 32 characters is required");
    if (defaultRole == null || !defaultRole.matches("[a-z][a-z0-9._-]{0,159}")) throw new IllegalStateException("Invalid default role key");
    this.authorizationUrl = URI.create(authorizationUrl.replaceAll("/+$", "") + "/");
    this.authorizationHeader = "Basic " + Base64.getEncoder().encodeToString(("provisioner:" + provisioningPassword).getBytes(StandardCharsets.UTF_8));
    this.defaultRole = defaultRole;
    this.json = json;
  }

  public String defaultRole() { return defaultRole; }

  /** Idempotent: an already active assignment is reported, not duplicated. */
  public Outcome assignDefaultRole(UUID hiveUserId) {
    try {
      HttpRequest request = HttpRequest.newBuilder(authorizationUrl.resolve("admin/roles/" + defaultRole + "/assignments"))
          .timeout(Duration.ofSeconds(10))
          .header("Authorization", authorizationHeader)
          .header("Content-Type", "application/json")
          .header(CorrelationId.HEADER, CorrelationId.current())
          .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("subject", "user:" + hiveUserId))))
          .build();
      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() / 100 == 2) return Outcome.ASSIGNED;
      if (response.statusCode() == 409) return Outcome.ALREADY_ASSIGNED;
      throw new IllegalStateException("Hive refused the role assignment (HTTP " + response.statusCode() + ")");
    } catch (IOException e) {
      throw new IllegalStateException("Hive authorization service unreachable", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted", e);
    }
  }
}
