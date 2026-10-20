package io.quarkiverse.langchain4j.mockapi.internal.config;

import java.util.List;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;

/**
 * Published by the mock API dev service for the application; not meant to be set by hand.
 */
@ConfigMapping(prefix = "quarkus.langchain4j.mock-api")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface MockApiRuntimeConfig {

    /**
     * The URL of the running mock.
     */
    Optional<String> url();

    /**
     * The endpoint config keys of every model in use, each as {@code key=url}, that the mock points at itself.
     */
    Optional<List<String>> overrides();
}
