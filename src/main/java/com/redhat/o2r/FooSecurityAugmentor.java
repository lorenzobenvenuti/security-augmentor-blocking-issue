package com.redhat.o2r;

import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.SecurityIdentityAugmentor;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class FooSecurityAugmentor implements SecurityIdentityAugmentor {

    private static final Logger LOGGER = LoggerFactory.getLogger(FooSecurityAugmentor.class);

    @Inject
    FooService fooService;

    @Override
    public Uni<SecurityIdentity> augment(
        SecurityIdentity securityIdentity,
        AuthenticationRequestContext authenticationRequestContext
    ) {
        if (securityIdentity.isAnonymous()) {
            return Uni.createFrom().item(securityIdentity);
        }
        return authenticationRequestContext.runBlocking(() -> {
            String principal = securityIdentity.getPrincipal().getName();
            LOGGER.info("Augmenting identity for {}", principal);
            return QuarkusSecurityIdentity.builder().setPrincipal(
                new QuarkusPrincipal(fooService.getIdentity(principal))
            ).build();
        });
    }

}
