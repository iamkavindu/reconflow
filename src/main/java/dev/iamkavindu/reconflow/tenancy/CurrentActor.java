package dev.iamkavindu.reconflow.tenancy;

import java.util.Optional;

public interface CurrentActor {

    Actor actor();

    default Optional<Actor> optionalActor() {
        return Optional.of(actor());
    }
}
