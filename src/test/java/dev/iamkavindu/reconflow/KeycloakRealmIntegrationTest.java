package dev.iamkavindu.reconflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import tools.jackson.databind.json.JsonMapper;

@Testcontainers
class KeycloakRealmIntegrationTest {

    @Container
    static final GenericContainer<?> KEYCLOAK = new GenericContainer<>(DockerImageName.parse("quay.io/keycloak/keycloak:26.8.1"))
            .withExposedPorts(8080)
            .withCopyFileToContainer(MountableFile.forHostPath("dev/keycloak/reconflow-realm.json"),
                    "/opt/keycloak/data/import/reconflow-realm.json")
            .withCommand("start-dev", "--import-realm")
            .waitingFor(Wait.forHttp("/realms/reconflow").forPort(8080))
            .withStartupTimeout(Duration.ofMinutes(3));

    @Test
    void importedRealmIssuesVerifiableTenantTokensForEachDemoRole() throws Exception {
        String issuer = "http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK.getMappedPort(8080) + "/realms/reconflow";
        var decoder = NimbusJwtDecoder.withJwkSetUri(issuer + "/protocol/openid-connect/certs").build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        String[] users = {"admin", "analyst", "viewer", "other-analyst"};
        try (HttpClient client = HttpClient.newHttpClient()) {
            for (int index = 0; index < users.length; index++) {
                var request = HttpRequest.newBuilder(URI.create(issuer + "/protocol/openid-connect/token"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString("grant_type=password&client_id=reconflow&username="
                                + users[index] + "&password=demo-password"))
                        .build();
                var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).as("demo token for %s", users[index]).isEqualTo(200);
                String token = JsonMapper.builder().build().readTree(response.body()).get("access_token").asString();
                var jwt = decoder.decode(token);
                assertThat(jwt.getSubject()).isEqualTo("10000000-0000-0000-0000-00000000000" + (index + 1));
                assertThat(jwt.getAudience()).contains("reconflow");
                String tenant = index == 3 ? "00000000-0000-0000-0000-000000000002" : "00000000-0000-0000-0000-000000000001";
                assertThat(jwt.getClaimAsString("tenant_id")).isEqualTo(tenant);
            }
        }
    }
}
