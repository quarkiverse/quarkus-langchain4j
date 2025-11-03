package io.quarkiverse.langchain4j.chroma.runtime;

import static io.quarkus.runtime.annotations.ConfigPhase.RUN_TIME;

import java.time.Duration;
import java.util.Optional;

import dev.langchain4j.store.embedding.chroma.ChromaApiVersion;
import io.quarkus.runtime.annotations.ConfigDocDefault;
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

    /**
     * Whether requests to Chroma should be logged
     */
    @ConfigDocDefault("false")
    @WithDefault("${quarkus.langchain4j.log-requests}")
    Optional<Boolean> logRequests();

    /**
     * Whether responses from Chroma should be logged
     */
    @ConfigDocDefault("false")
    @WithDefault("${quarkus.langchain4j.log-requests}")
    Optional<Boolean> logResponses();

    /**
     * The Chroma API version to use.
     * V1 is deprecated (Chroma 0.x) and its support will be removed in the future.
     * Please use Chroma 1.x which uses the V2 API.
     */
    @WithDefault("V2")
    ChromaApiVersion apiVersion();

}
