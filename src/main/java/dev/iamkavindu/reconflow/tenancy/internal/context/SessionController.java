package dev.iamkavindu.reconflow.tenancy.internal.context;

import dev.iamkavindu.reconflow.tenancy.Actor;
import dev.iamkavindu.reconflow.tenancy.CurrentActor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class SessionController {

    private final CurrentActor currentActor;

    SessionController(CurrentActor currentActor) {
        this.currentActor = currentActor;
    }

    @GetMapping("/api/session")
    ResponseEntity<Actor> session() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(currentActor.actor());
    }
}
