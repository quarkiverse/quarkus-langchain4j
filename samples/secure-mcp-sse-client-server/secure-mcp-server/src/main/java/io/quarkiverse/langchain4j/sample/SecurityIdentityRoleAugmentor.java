package io.quarkiverse.langchain4j.sample;

import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.oidc.UserInfo;
import io.quarkus.oidc.runtime.OidcUtils;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.SecurityIdentityAugmentor;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SecurityIdentityRoleAugmentor implements SecurityIdentityAugmentor {

    @WithSession
    @Override
    public Uni<SecurityIdentity> augment(SecurityIdentity identity, AuthenticationRequestContext context) {
        QuarkusSecurityIdentity.Builder builder = QuarkusSecurityIdentity.builder(identity);
        UserInfo userInfo = identity.getAttribute(OidcUtils.USER_INFO_ATTRIBUTE); 
        return Identity.findByName(userInfo.getName())
            		.invoke(id -> builder.addPermissionAsString(id.permission))
            		.map(v -> builder.build());
    }
}
