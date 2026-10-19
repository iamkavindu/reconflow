package dev.iamkavindu.reconflow.provider.internal.messaging;

import dev.iamkavindu.reconflow.provider.ProviderTransactionCommands;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
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

    @KafkaListener(
            topics = "${reconflow.kafka.provider-events-topic}",
            groupId = "${reconflow.kafka.provider-events-group}")
    void onMessage(String message) {
        providerTransactionCommands.ingestProviderEvent(messageMapper.toCommand(message));
    }
}
