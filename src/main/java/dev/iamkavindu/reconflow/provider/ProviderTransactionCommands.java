package dev.iamkavindu.reconflow.provider;

public interface ProviderTransactionCommands {

    ProviderTransactionView ingestProviderEvent(IngestProviderEventCommand command);
}
