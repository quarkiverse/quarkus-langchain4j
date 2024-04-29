package io.quarkiverse.langchain4j.memorystore.redis.deployment;

import static io.quarkus.runtime.annotations.ConfigPhase.BUILD_TIME;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;

@ConfigRoot(phase = BUILD_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.memorystore.redis")
public interface RedisMemoryStoreBuildTimeConfig {

    /**
     * The name of the Redis client to use. These clients are configured by means of the `redis-client` extension.
     * If unspecified, it will use the default Redis client.
     */
    Optional<String> clientName();
}
