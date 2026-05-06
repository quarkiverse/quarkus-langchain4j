package io.quarkiverse.langchain4j.redis;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface RedisNamedStoreBuildTimeConfig {

    /**
     * The name of the Redis client to use. These clients are configured by means of the `redis-client` extension.
     * If not set, the default Redis client will be used.
     */
    @WithDefault("<default>")
    Optional<String> clientName();
}
