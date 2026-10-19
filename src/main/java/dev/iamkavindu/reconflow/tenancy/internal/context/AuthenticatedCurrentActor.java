package dev.iamkavindu.reconflow.tenancy.internal.context;

import dev.iamkavindu.reconflow.tenancy.Actor;
import dev.iamkavindu.reconflow.tenancy.CurrentActor;
import dev.iamkavindu.reconflow.tenancy.CurrentTenant;
import dev.iamkavindu.reconflow.tenancy.OperationNotAuthorizedException;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@Primary
@ConditionalOnProperty(name = "reconflow.security.enabled", havingValue = "true", matchIfMissing = true)
class AuthenticatedCurrentActor implements CurrentActor, CurrentTenant {

    @Override
    public Actor actor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof Actor actor)) {
            throw new OperationNotAuthorizedException("an active tenant membership is required");
        }
        return actor;
    }

    @Override
    public TenantId tenantId() {
        return actor().tenantId();
    }
}
