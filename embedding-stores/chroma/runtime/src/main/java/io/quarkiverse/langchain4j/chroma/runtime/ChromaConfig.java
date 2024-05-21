package io.quarkiverse.langchain4j.chroma.runtime;

import static io.quarkus.runtime.annotations.ConfigPhase.RUN_TIME;

import java.time.Duration;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = RUN_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.chroma")
public interface ChromaConfig {

    /**
     * URL where the Chroma database is listening for requests
     */
    String url();

    /**
     * The collection name.
     */
    @WithDefault("default")
    String collectionName();

    /**
     * The timeout duration for the Chroma client. If not specified, 5 seconds will be used.
     */
    Optional<Duration> timeout();

}
