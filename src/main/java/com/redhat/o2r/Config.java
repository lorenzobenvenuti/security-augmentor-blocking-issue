package com.redhat.o2r;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.time.Duration;
import java.util.List;

@ConfigMapping(prefix = "foo")
public interface Config {

    @WithDefault("PT20S")
    Duration delay();

}
