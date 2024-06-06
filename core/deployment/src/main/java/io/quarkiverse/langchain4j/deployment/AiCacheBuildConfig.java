package io.quarkiverse.langchain4j.deployment;

import static io.quarkus.runtime.annotations.ConfigPhase.BUILD_TIME;

import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;

@ConfigRoot(phase = BUILD_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.cache")
public interface AiCacheBuildConfig {

    /**
     * Ai Cache embedding model related settings
     */
    CacheEmbeddingModelConfig embedding();
}
