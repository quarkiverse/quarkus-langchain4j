package io.quarkiverse.langchain4j.runtime.cache.config;

import static io.quarkus.runtime.annotations.ConfigPhase.RUN_TIME;

import java.time.Duration;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = RUN_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.cache")
public interface AiCacheConfig {

    /**
     * Threshold used during semantic search to validate whether a cache result should be returned.
     */
    @WithDefault("1")
    double threshold();

    /**
     * Maximum number of messages to cache.
     */
    @WithDefault("1")
    int maxSize();

    /**
     * Time to live for messages stored in the cache.
     */
    Optional<Duration> ttl();

    /**
     * Allow to customize the embedding operation.
     */
    AiCacheEmbeddingConfig embedding();
}
