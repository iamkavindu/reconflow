package dev.iamkavindu.reconflow.reconciliation.internal.application;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import dev.iamkavindu.reconflow.tenancy.TenantId;
import dev.iamkavindu.reconflow.transactions.TransactionId;
import dev.iamkavindu.reconflow.transactions.events.TransactionRecorded;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TransactionRecordedListenerTest {

    @Test
    void delegatesUsingTenantReferenceAndEventTrace() {
        AutomaticReconciliation reconciliation = mock(AutomaticReconciliation.class);
        TransactionRecordedListener listener = new TransactionRecordedListener(reconciliation);
        Instant recordedAt = Instant.parse("2026-10-10T09:25:00Z");
        TransactionRecorded event = new TransactionRecorded(
                UUID.randomUUID(),
                new TenantId(UUID.randomUUID()),
                new TransactionId(UUID.randomUUID()),
                "ORD-1001",
                recordedAt,
                UUID.randomUUID());

        listener.on(event);

        verify(reconciliation)
                .attempt(event.tenantId(), event.businessReference(), event.correlationId(), event.eventId());
    }
}
