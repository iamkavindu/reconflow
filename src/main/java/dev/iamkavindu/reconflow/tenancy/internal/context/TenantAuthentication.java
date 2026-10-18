package dev.iamkavindu.reconflow.tenancy.internal.context;

import dev.iamkavindu.reconflow.tenancy.Actor;
import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

final class TenantAuthentication extends AbstractAuthenticationToken {

    private final Actor actor;

    TenantAuthentication(Actor actor) {
        super(List.of(new SimpleGrantedAuthority("ROLE_" + actor.role().name())));
        this.actor = actor;
        setAuthenticated(true);
    }

    @Override
    public Object getPrincipal() {
        return actor;
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public String getName() {
        return actor.subject();
    }
}
