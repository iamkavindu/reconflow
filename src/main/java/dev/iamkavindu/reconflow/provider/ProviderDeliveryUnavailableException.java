package dev.iamkavindu.reconflow.provider;

public class ProviderDeliveryUnavailableException extends RuntimeException {
    public ProviderDeliveryUnavailableException() {
        super("Provider event delivery is unavailable. Retry using the same provider event identity.");
    }
}
