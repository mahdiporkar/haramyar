package ir.haramyar.kit.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class HiveRequestIdentityFilterTest {
  private final IdentityBindings bindings = mock(IdentityBindings.class);
  private final HiveRequestIdentityFilter filter = new HiveRequestIdentityFilter(bindings, new ObjectMapper());
  private final FilterChain chain = mock(FilterChain.class);

  @AfterEach void clear() { SecurityContextHolder.clearContext(); }

  private static Jwt jwt(Map<String, Object> claims) {
    var builder = Jwt.withTokenValue("token").header("alg", "RS256").subject("kc-subject").issuer("http://localhost:8080/realms/haramyar")
        .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
    claims.forEach(builder::claim);
    return builder.build();
  }

  @Test
  void validatedTokenAndHiveUserIdBecomeActor() throws Exception {
    UUID user = UUID.randomUUID();
    when(bindings.verify(anyString(), anyString(), any())).thenReturn(true);
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt(Map.of(
        "preferred_username", "+989121234567", "name", "Zahra Moradi", "realm_access", Map.of("roles", List.of("haramyar-staff")), "phone_number_verified", true))));
    var request = new MockHttpServletRequest();
    request.addHeader("X-Hive-User-Id", user.toString());
    filter.doFilter(request, new MockHttpServletResponse(), chain);
    Actor actor = CurrentActor.require();
    assertThat(actor.userId()).isEqualTo(user);
    assertThat(actor.username()).isEqualTo("+989121234567");
    assertThat(actor.displayName()).isEqualTo("Zahra Moradi");
    assertThat(actor.hasRealmRole("haramyar-staff")).isTrue();
    assertThat(actor.mobileVerified()).isTrue();
    verify(chain).doFilter(any(), any());
  }

  @Test
  void hiveUserIdWithoutCredentialIsRefused() throws Exception {
    var request = new MockHttpServletRequest();
    request.addHeader("X-Hive-User-Id", UUID.randomUUID().toString());
    var response = new MockHttpServletResponse();
    filter.doFilter(request, response, chain);
    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getContentAsString()).contains("IDENTITY_CONTEXT_INVALID");
    verify(chain, never()).doFilter(any(), any());
  }

  @Test
  void credentialWithoutOrWithMalformedHiveUserIdIsRefused() throws Exception {
    for (String header : new String[] {null, "not-a-uuid", "../" + UUID.randomUUID()}) {
      SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt(Map.of())));
      var request = new MockHttpServletRequest();
      if (header != null) request.addHeader("X-Hive-User-Id", header);
      var response = new MockHttpServletResponse();
      filter.doFilter(request, response, chain);
      assertThat(response.getStatus()).as("header %s", header).isEqualTo(401);
    }
    verify(chain, never()).doFilter(any(), any());
  }

  @Test
  void mismatchWithPinnedBindingIsRefused() throws Exception {
    when(bindings.verify(anyString(), anyString(), any())).thenReturn(false);
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt(Map.of())));
    var request = new MockHttpServletRequest();
    request.addHeader("X-Hive-User-Id", UUID.randomUUID().toString());
    var response = new MockHttpServletResponse();
    filter.doFilter(request, response, chain);
    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(CurrentActor.optional()).isEmpty();
  }

  @Test
  void anonymousRequestPassesWithoutActor() throws Exception {
    filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);
    verify(chain).doFilter(any(), any());
    assertThat(CurrentActor.optional()).isEmpty();
  }

  @Test
  void unverifiedMobileIsNeverReportedVerified() {
    Actor actor = HiveRequestIdentityFilter.actorOf(jwt(Map.of("phone_number_verified", "yes")), UUID.randomUUID());
    assertThat(actor.mobileVerified()).isFalse();
    assertThat(HiveRequestIdentityFilter.actorOf(jwt(Map.of()), UUID.randomUUID()).mobileVerified()).isFalse();
  }
}
