package dev.iamkavindu.reconflow.provider.internal.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "settlements", schema = "provider")
class SettlementJpaEntity {

    @Id
    private UUID id;

    protected SettlementJpaEntity() {}
}
