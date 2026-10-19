package dev.iamkavindu.reconflow.reporting.internal;

import dev.iamkavindu.reconflow.reporting.Dashboard;
import dev.iamkavindu.reconflow.reporting.DashboardQueries;
import dev.iamkavindu.reconflow.tenancy.CurrentActor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class DashboardController {
    private final DashboardQueries queries;
    private final CurrentActor currentActor;

    DashboardController(DashboardQueries queries, CurrentActor currentActor) {
        this.queries = queries;
        this.currentActor = currentActor;
    }

    @GetMapping("/api/dashboard")
    Dashboard get() {
        return queries.get(currentActor.actor().tenantId());
    }
}
