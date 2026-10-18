package dev.iamkavindu.reconflow.tenancy.internal.context;

import dev.iamkavindu.reconflow.tenancy.Actor;
import dev.iamkavindu.reconflow.tenancy.CurrentActor;
import dev.iamkavindu.reconflow.tenancy.OperationNotAuthorizedException;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
class UnauthenticatedCurrentActor implements CurrentActor {

    @Override
    public Optional<Actor> optionalActor() {
        return Optional.empty();
    }

    @Override
    public Actor actor() {
        throw new OperationNotAuthorizedException("an authenticated actor is required");
    }
}
