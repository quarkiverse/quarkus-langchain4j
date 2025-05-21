package io.quarkiverse.langchain4j.sample;

import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.SecurityIdentityAugmentor;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

@ApplicationScoped
public class SecurityIdentityPermissionAugmentor implements SecurityIdentityAugmentor {

    @Inject 
    HibernateBlockingAugmentor hibernateBlockingAugmentor;

    @Override
    public Uni<SecurityIdentity> augment(SecurityIdentity identity, AuthenticationRequestContext context) {
        return context.runBlocking(() -> hibernateBlockingAugmentor.augment(identity));
    }

    @ApplicationScoped
    static class HibernateBlockingAugmentor {

        @ActivateRequestContext
        public SecurityIdentity augment(SecurityIdentity securityIdentity) {
            Identity identity = Identity.findByName(securityIdentity.getPrincipal().getName());
            
            QuarkusSecurityIdentity.Builder builder = QuarkusSecurityIdentity.builder(securityIdentity);
            return builder.addPermissionAsString(identity.permission).build();
        }
    }
}
