
package com.redhat.o2r;

import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class FooService {

    @Inject
    Config config;


    @CacheResult(cacheName = "foo")
    public String getIdentity(String name) {
        LoggerFactory.getLogger(FooService.class).info("name={}", name);
        try {
            Thread.sleep(config.delay().toMillis());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return name;
    }

}
