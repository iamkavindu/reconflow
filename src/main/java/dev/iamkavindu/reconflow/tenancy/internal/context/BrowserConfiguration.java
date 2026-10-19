package dev.iamkavindu.reconflow.tenancy.internal.context;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class BrowserConfiguration {
    private final Settings settings;

    BrowserConfiguration(
            @Value("${reconflow.security.issuer}") URI issuer,
            @Value("${reconflow.security.audience}") String clientId) {
        this.settings = new Settings(issuer.toString(), clientId);
    }

    @GetMapping("/ui/config")
    Settings settings() {
        return settings;
    }

    record Settings(String issuer, String clientId) {}
}
