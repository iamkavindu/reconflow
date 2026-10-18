package dev.iamkavindu.reconflow.tenancy.internal.context;

import dev.iamkavindu.reconflow.tenancy.TenantId;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "reconflow.security.enabled", havingValue = "true", matchIfMissing = true)
class ApiSecurityConfiguration {

    @Bean
    JwtDecoder jwtDecoder(
            @Value("${reconflow.security.issuer}") String issuer,
            @Value("${reconflow.security.jwk-set-uri}") String jwkSetUri,
            @Value("${reconflow.security.audience}") String audience) {
        var decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer),
                jwt -> jwt.getExpiresAt() != null && jwt.getAudience().contains(audience)
                        ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"))));
        return decoder;
    }

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, TenantMemberships memberships) throws Exception {
        // This chain accepts bearer headers only; it never authenticates cookies, Basic, or sessions.
        http.csrf(csrf -> csrf.disable());
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        http.requestCache(cache -> cache.disable());
        http.authorizeHttpRequests(requests -> requests.anyRequest().authenticated());
        http.exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, error) -> unauthorized(response))
                .accessDeniedHandler((request, response, error) -> problem(response, 403, "Forbidden")));
        http.oauth2ResourceServer(oauth -> oauth
                .authenticationEntryPoint((request, response, error) -> unauthorized(response))
                .jwt(jwt -> jwt.jwtAuthenticationConverter(token -> authenticate(token, memberships))));
        return http.build();
    }

    private TenantAuthentication authenticate(Jwt jwt, TenantMemberships memberships) {
        TenantId tenantId;
        try {
            tenantId = new TenantId(UUID.fromString(jwt.getClaimAsString("tenant_id")));
            if (jwt.getSubject() == null || jwt.getSubject().isBlank()) {
                throw new IllegalArgumentException("missing subject");
            }
        } catch (IllegalArgumentException | NullPointerException error) {
            throw new OAuth2AuthenticationException(new OAuth2Error("invalid_token"));
        }
        var actor = memberships.findEnabled(tenantId, jwt.getSubject())
                .orElseThrow(() -> new OAuth2AuthenticationException(new OAuth2Error("invalid_token")));
        return new TenantAuthentication(actor);
    }

    private static void unauthorized(HttpServletResponse response) throws IOException {
        response.setHeader("WWW-Authenticate", "Bearer");
        problem(response, 401, "Unauthorized");
    }

    private static void problem(HttpServletResponse response, int status, String title) throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"" + title + "\",\"status\":" + status + "}");
    }
}
