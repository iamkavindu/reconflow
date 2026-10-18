package dev.iamkavindu.reconflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@SpringBootTest(properties = {"reconflow.kafka.enabled=false", "reconflow.reconciliation.scheduling-enabled=false"})
class AuthenticatedApiIntegrationTest {

    private static final String TENANT = "00000000-0000-0000-0000-000000000001";
    private static final String OTHER_TENANT = "00000000-0000-0000-0000-000000000002";
    private static final RSAKey KEY;
    private static final HttpServer JWKS;
    private static final String ISSUER;

    static {
        try {
            KEY = new RSAKeyGenerator(2048).keyID("test-key").generate();
            JWKS = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            byte[] keys = new JWKSet(KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            JWKS.createContext("/certs", exchange -> {
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, keys.length);
                try (var body = exchange.getResponseBody()) {
                    body.write(keys);
                }
            });
            JWKS.start();
            ISSUER = "http://127.0.0.1:" + JWKS.getAddress().getPort();
        } catch (JOSEException | IOException error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    @DynamicPropertySource
    static void oidc(DynamicPropertyRegistry properties) {
        properties.add("reconflow.security.issuer", () -> ISSUER);
        properties.add("reconflow.security.jwk-set-uri", () -> ISSUER + "/certs");
    }

    @AfterAll
    static void stopIssuer() {
        JWKS.stop(0);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void memberships() {
        jdbc.sql("INSERT INTO tenancy.tenants (id, name) VALUES (?, ?), (?, ?) ON CONFLICT DO NOTHING")
                .params(UUID.fromString(TENANT), "Tenant one", UUID.fromString(OTHER_TENANT), "Tenant two")
                .update();
        membership(TENANT, "admin", "ADMIN");
        membership(TENANT, "analyst", "ANALYST");
        membership(TENANT, "viewer", "VIEWER");
        membership(OTHER_TENANT, "other", "ANALYST");
    }

    @Test
    void requiresVerifiedBearerWithIssuerAudienceExpiryAndMembership() throws Exception {
        mvc.perform(get("/api/session")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/session").header("Authorization", "Basic YWRtaW46YWRtaW4="))
                .andExpect(status().isUnauthorized());
        String[][] invalid = {{"wrong-issuer", "reconflow", TENANT}, {ISSUER, "wrong-audience", TENANT},
            {ISSUER, "reconflow", "bad-tenant"}};
        for (String[] claims : invalid) {
            mvc.perform(get("/api/session").header("Authorization",
                            token("analyst", claims[2], claims[0], claims[1], Instant.now().plusSeconds(300), KEY)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        }
        mvc.perform(get("/api/session").header("Authorization",
                        token("analyst", TENANT, ISSUER, "reconflow", Instant.now().minusSeconds(300), KEY)))
                .andExpect(status().isUnauthorized());
        RSAKey untrusted = new RSAKeyGenerator(2048).keyID("test-key").generate();
        mvc.perform(get("/api/session").header("Authorization",
                        token("analyst", TENANT, ISSUER, "reconflow", Instant.now().plusSeconds(300), untrusted)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/session").header("Authorization", token("unknown", TENANT)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/session").header("Authorization", token("analyst", OTHER_TENANT)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void derivesTenantAndRoleFromActiveMembershipOnEveryRequest() throws Exception {
        String bearer = token("viewer", TENANT);
        mvc.perform(get("/api/session").header("Authorization", bearer).header("X-Tenant-Id", OTHER_TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId.value").value(TENANT))
                .andExpect(jsonPath("$.role").value("VIEWER"));
        mvc.perform(post("/api/transactions").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(transaction()))
                .andExpect(status().isForbidden());
        membership(TENANT, "viewer", "ANALYST");
        mvc.perform(post("/api/transactions").header("Authorization", bearer)
                        .header("X-Tenant-Id", OTHER_TENANT)
                        .contentType(MediaType.APPLICATION_JSON).content(transaction()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantId.value").value(TENANT));
        jdbc.sql("UPDATE tenancy.tenant_memberships SET enabled = false WHERE oidc_subject = 'viewer'")
                .update();
        mvc.perform(get("/api/session").header("Authorization", bearer)).andExpect(status().isUnauthorized());
    }

    @Test
    void protectsFinancialReadsAndValidatesInputs() throws Exception {
        var created = mvc.perform(post("/api/transactions").header("Authorization", token("admin", TENANT))
                        .contentType(MediaType.APPLICATION_JSON).content(transaction()))
                .andExpect(status().isCreated()).andReturn();
        String location = created.getResponse().getHeader("Location");
        assertThat(location).startsWith("/api/transactions/");
        mvc.perform(get(location).header("Authorization", token("viewer", TENANT)))
                .andExpect(status().isOk());
        mvc.perform(get(location).header("Authorization", token("other", OTHER_TENANT)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/transactions").header("Authorization", token("analyst", TENANT))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/cases/not-a-uuid").header("Authorization", token("analyst", TENANT)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/v3/api-docs").header("Authorization", token("analyst", TENANT)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.paths['/api/transactions']").exists());
    }

    @Test
    void exposesTenantScopedCaseLifecycleWithoutOverwritingResolution() throws Exception {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO cases.cases
                        (id, tenant_id, reconciliation_id, status, opened_at, updated_at, notes, version)
                        VALUES (?, ?, ?, 'OPEN', now(), now(), '[]', 0)
                        """)
                .params(id, UUID.fromString(TENANT), UUID.randomUUID()).update();
        String path = "/api/cases/" + id;
        mvc.perform(get(path).header("Authorization", token("other", OTHER_TENANT)))
                .andExpect(status().isNotFound());
        mvc.perform(post(path + "/review").header("Authorization", token("viewer", TENANT)))
                .andExpect(status().isForbidden());
        mvc.perform(post(path + "/review").header("Authorization", token("analyst", TENANT)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UNDER_REVIEW"));
        mvc.perform(post(path + "/notes").header("Authorization", token("analyst", TENANT))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"Compared the immutable evidence\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.notes[0].addedBy").value("analyst"));
        String resolution = """
                {"type":"MANUAL_REVIEW_COMPLETED","reason":"Evidence reviewed","evidenceReferences":[]}
                """;
        mvc.perform(post(path + "/resolution").header("Authorization", token("analyst", TENANT))
                        .contentType(MediaType.APPLICATION_JSON).content(resolution))
                .andExpect(status().isOk()).andExpect(jsonPath("$.resolution.resolvedBy").value("analyst"));
        mvc.perform(post(path + "/resolution").header("Authorization", token("admin", TENANT))
                        .contentType(MediaType.APPLICATION_JSON).content(resolution))
                .andExpect(status().isConflict());
    }

    private void membership(String tenant, String subject, String role) {
        jdbc.sql("""
                        INSERT INTO tenancy.tenant_memberships (tenant_id, oidc_subject, role) VALUES (?, ?, ?)
                        ON CONFLICT (tenant_id, oidc_subject) DO UPDATE SET role = EXCLUDED.role, enabled = true
                        """)
                .params(UUID.fromString(tenant), subject, role).update();
    }

    private String transaction() {
        return "{\"businessReference\":\"" + UUID.randomUUID()
                + "\",\"type\":\"PAYMENT\",\"amount\":25.00,\"currency\":\"USD\",\"occurredAt\":\"2026-10-18T12:00:00Z\"}";
    }

    private static String token(String subject, String tenant) throws JOSEException {
        return token(subject, tenant, ISSUER, "reconflow", Instant.now().plusSeconds(300), KEY);
    }

    private static String token(String subject, String tenant, String issuer, String audience, Instant expiry, RSAKey key)
            throws JOSEException {
        var claims = new JWTClaimsSet.Builder().issuer(issuer).subject(subject).audience(audience)
                .issueTime(Date.from(Instant.now().minusSeconds(600))).expirationTime(Date.from(expiry))
                .claim("tenant_id", tenant).claim("realm_access", java.util.Map.of("roles", java.util.List.of("ADMIN")))
                .build();
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key").build(), claims);
        jwt.sign(new RSASSASigner(key));
        return "Bearer " + jwt.serialize();
    }
}
