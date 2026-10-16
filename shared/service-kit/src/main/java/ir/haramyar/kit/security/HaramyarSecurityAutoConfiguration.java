package ir.haramyar.kit.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.hiveplatform.spring.CorrelationId;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.savedrequest.NullRequestCache;

/**
 * Stateless resource-server configuration for HaramYar backends. Backends are reachable only from the Hive BFF network;
 * they accept the user token the BFF forwards ({@code FORWARD_TOKEN}) and never browser cookies, so CSRF does not apply
 * here (the BFF enforces it for browsers).
 */
@AutoConfiguration
@EnableConfigurationProperties(HaramyarSecurityProperties.class)
public class HaramyarSecurityAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  JwtDecoder haramyarJwtDecoder(HaramyarSecurityProperties properties) {
    if (properties.issuer() == null || properties.issuer().isBlank() || properties.jwkSetUri() == null || properties.jwkSetUri().isBlank())
      throw new IllegalStateException("haramyar.security.issuer and haramyar.security.jwk-set-uri are required");
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri()).build();
    OAuth2TokenValidator<Jwt> issuedToClient = jwt -> {
      if (properties.clientId() == null || properties.clientId().isBlank() || properties.clientId().equals(jwt.getClaimAsString("azp"))) return OAuth2TokenValidatorResult.success();
      return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Token was not issued to the HaramYar BFF client", null));
    };
    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(properties.issuer()), issuedToClient));
    return decoder;
  }

  @Bean
  ServiceStartupGuard haramyarStartupGuard(org.springframework.core.env.Environment environment, HaramyarSecurityProperties properties) {
    return new ServiceStartupGuard(environment, properties);
  }

  @Bean
  @ConditionalOnMissingBean
  IdentityBindings identityBindings(ObjectProvider<JdbcTemplate> db, HaramyarSecurityProperties properties) {
    JdbcTemplate template = db.getIfAvailable();
    return properties.identityBinding() && template != null ? new IdentityBindings(template) : null;
  }

  @Bean
  @ConditionalOnMissingBean(SecurityFilterChain.class)
  SecurityFilterChain haramyarSecurity(HttpSecurity http, HaramyarSecurityProperties properties, ObjectProvider<IdentityBindings> bindings, ObjectMapper json) throws Exception {
    List<String> open = new java.util.ArrayList<>(properties.publicPaths());
    open.addAll(List.of("/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness", "/error"));
    return http
        .csrf(csrf -> csrf.disable())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .requestCache(cache -> cache.requestCache(new NullRequestCache()))
        .authorizeHttpRequests(auth -> auth.requestMatchers(open.toArray(String[]::new)).permitAll().anyRequest().authenticated())
        .oauth2ResourceServer(server -> server.jwt(jwt -> {}).authenticationEntryPoint((request, response, failure) -> write(json, response, 401, "AUTHENTICATION_REQUIRED", "A valid forwarded credential is required")))
        .exceptionHandling(errors -> errors
            .authenticationEntryPoint((request, response, failure) -> write(json, response, 401, "AUTHENTICATION_REQUIRED", "Sign in to continue"))
            .accessDeniedHandler((request, response, failure) -> write(json, response, 403, "ACCESS_DENIED", "Access denied")))
        .addFilterAfter(new HiveRequestIdentityFilter(bindings.getIfAvailable(), json), BearerTokenAuthenticationFilter.class)
        .build();
  }

  private static void write(ObjectMapper json, jakarta.servlet.http.HttpServletResponse response, int status, String code, String message) throws java.io.IOException {
    response.setStatus(status);
    response.setContentType("application/json");
    var body = new LinkedHashMap<String, Object>();
    body.put("code", code);
    body.put("message", message);
    body.put("correlationId", CorrelationId.current());
    json.writeValue(response.getOutputStream(), body);
  }
}
