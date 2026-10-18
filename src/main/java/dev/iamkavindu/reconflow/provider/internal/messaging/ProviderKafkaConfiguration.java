package dev.iamkavindu.reconflow.provider.internal.messaging;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaRetryTopic;

@Configuration(proxyBeanMethods = false)
@EnableKafkaRetryTopic
@ConditionalOnProperty(prefix = "reconflow.kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
class ProviderKafkaConfiguration {}
