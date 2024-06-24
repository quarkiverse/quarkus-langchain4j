package io.quarkiverse.langchain4j.sample;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "customer")
public interface CustomerConfig {

    String name();

    String email();
}
