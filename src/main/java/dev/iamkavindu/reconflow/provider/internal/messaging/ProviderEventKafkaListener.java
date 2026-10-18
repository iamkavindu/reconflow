package dev.iamkavindu.reconflow.provider.internal.messaging;

import dev.iamkavindu.reconflow.provider.ProviderTransactionCommands;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@ConditionalOnProperty(prefix = "reconflow.kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
class ProviderEventKafkaListener {

    private final ProviderEventMessageMapper messageMapper;
    private final ProviderTransactionCommands providerTransactionCommands;

    ProviderEventKafkaListener(JsonMapper jsonMapper, ProviderTransactionCommands providerTransactionCommands) {
        this.messageMapper = new ProviderEventMessageMapper(jsonMapper);
        this.providerTransactionCommands = providerTransactionCommands;
    }

    @RetryableTopic(
            attempts = "3",
            backOff = @BackOff(delay = 1000, multiplier = 2.0, maxDelay = 5000),
            dltStrategy = DltStrategy.FAIL_ON_ERROR,
            autoStartDltHandler = "false",
            numPartitions = "1",
            replicationFactor = "1")
    @KafkaListener(
            topics = "${reconflow.kafka.provider-events-topic}",
            groupId = "${reconflow.kafka.provider-events-group}")
    void onMessage(String message) {
        providerTransactionCommands.ingestProviderEvent(messageMapper.toCommand(message));
    }
}
