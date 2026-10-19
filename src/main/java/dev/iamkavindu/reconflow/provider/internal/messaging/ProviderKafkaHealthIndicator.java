package dev.iamkavindu.reconflow.provider.internal.messaging;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.kafka.core.KafkaAdmin;

@Component("providerKafkaHealthIndicator")
class ProviderKafkaHealthIndicator implements HealthIndicator {
    private final Map<String, Object> properties;
    private final boolean enabled;

    ProviderKafkaHealthIndicator(
            KafkaAdmin kafkaAdmin,
            @Value("${reconflow.kafka.enabled:true}") boolean enabled) {
        this.properties = new HashMap<>(kafkaAdmin.getConfigurationProperties());
        this.properties.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 1500);
        this.properties.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 1500);
        this.enabled = enabled;
    }

    @Override
    public Health health() {
        if (!enabled) {
            return Health.unknown().build();
        }
        var admin = Admin.create(properties);
        try {
            return admin.describeCluster().nodes().get(2, TimeUnit.SECONDS).isEmpty()
                    ? Health.down().build()
                    : Health.up().build();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return Health.down().build();
        } catch (Exception error) {
            return Health.down().build();
        } finally {
            admin.close(Duration.ofMillis(500));
        }
    }
}
