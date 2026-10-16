package ir.haramyar.kit.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.hiveplatform.spring.CorrelationId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Turns the BFF's forwarded credentials into an {@link Actor}. Runs after bearer-token validation:
 * <ul>
 *   <li>a validated token requires a well-formed {@code X-Hive-User-Id} (the BFF always sends both);</li>
 *   <li>{@code X-Hive-User-Id} without a validated token is refused, so the header alone never identifies anyone;</li>
 *   <li>the (issuer, subject) to Hive user binding is pinned on first use.</li>
 * </ul>
 */
public class HiveRequestIdentityFilter extends OncePerRequestFilter {
  public static final String HIVE_USER_HEADER = "X-Hive-User-Id";
  private final IdentityBindings bindings;
  private final ObjectMapper json;

  public HiveRequestIdentityFilter(IdentityBindings bindings, ObjectMapper json) {
    this.bindings = bindings;
    this.json = json;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    String header = request.getHeader(HIVE_USER_HEADER);
    if (authentication instanceof JwtAuthenticationToken token) {
      UUID userId = parse(header);
      if (userId == null) {
        reject(response, "IDENTITY_CONTEXT_INVALID", "The canonical user id is missing or malformed");
        return;
      }
      Jwt jwt = token.getToken();
      String issuer = jwt.getIssuer() == null ? "" : jwt.getIssuer().toString();
      if (bindings != null && !bindings.verify(issuer, jwt.getSubject(), userId)) {
        reject(response, "IDENTITY_CONTEXT_INVALID", "The canonical user id does not match the authenticated subject");
        return;
      }
      var context = SecurityContextHolder.createEmptyContext();
      context.setAuthentication(new ActorAuthentication(actorOf(jwt, userId)));
      SecurityContextHolder.setContext(context);
    } else if (header != null) {
      reject(response, "IDENTITY_CONTEXT_INVALID", "A canonical user id requires a forwarded credential");
      return;
    }
    chain.doFilter(request, response);
  }

  static Actor actorOf(Jwt jwt, UUID userId) {
    String username = jwt.getClaimAsString("preferred_username");
    String name = jwt.getClaimAsString("name");
    if (name == null || name.isBlank()) name = username == null ? jwt.getSubject() : username;
    return new Actor(userId, jwt.getSubject(), username, name, realmRoles(jwt), Boolean.parseBoolean(String.valueOf(jwt.getClaims().get("phone_number_verified"))));
  }

  private static Set<String> realmRoles(Jwt jwt) {
    Object realmAccess = jwt.getClaims().get("realm_access");
    if (realmAccess instanceof Map<?, ?> map && map.get("roles") instanceof Collection<?> roles) {
      var names = new LinkedHashSet<String>();
      roles.forEach(role -> names.add(String.valueOf(role)));
      return names;
    }
    return Set.of();
  }

  private static UUID parse(String header) {
    if (header == null || header.length() != 36) return null;
    try {
      return UUID.fromString(header);
    } catch (IllegalArgumentException malformed) {
      return null;
    }
  }

  private void reject(HttpServletResponse response, String code, String message) throws IOException {
    SecurityContextHolder.clearContext();
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType("application/json");
    var body = new LinkedHashMap<String, Object>();
    body.put("code", code);
    body.put("message", message);
    body.put("correlationId", CorrelationId.current());
    json.writeValue(response.getOutputStream(), body);
  }
}
