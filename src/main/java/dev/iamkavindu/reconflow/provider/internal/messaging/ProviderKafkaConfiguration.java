package dev.iamkavindu.reconflow.provider.internal.messaging;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaRetryTopic;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
@EnableKafkaRetryTopic
@ConditionalOnProperty(prefix = "reconflow.kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
class ProviderKafkaConfiguration {

    @Bean
    ThreadPoolTaskScheduler providerRetryScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("provider-retry-");
        return scheduler;
    }
}
