package dev.iamkavindu.reconflow.provider;

public interface ProviderSimulationCommands {
    boolean available();
    ProviderEventReceipt simulate(SimulateProviderEventCommand command);
}
