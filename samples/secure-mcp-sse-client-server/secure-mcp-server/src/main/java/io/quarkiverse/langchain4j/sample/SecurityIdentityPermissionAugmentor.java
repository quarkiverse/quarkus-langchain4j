package io.quarkiverse.langchain4j.sample;

import io.quarkus.oidc.UserInfo;
import io.quarkus.oidc.runtime.OidcUtils;
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
            QuarkusSecurityIdentity.Builder builder = QuarkusSecurityIdentity.builder(securityIdentity);
            UserInfo userInfo = securityIdentity.getAttribute(OidcUtils.USER_INFO_ATTRIBUTE); 
            Identity identity = Identity.findByName(userInfo.getName());
            return builder.addPermissionAsString(identity.permission).build();
        }
    }
}
