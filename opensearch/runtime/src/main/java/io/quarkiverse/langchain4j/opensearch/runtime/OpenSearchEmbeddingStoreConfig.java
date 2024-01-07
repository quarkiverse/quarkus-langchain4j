package io.quarkiverse.langchain4j.opensearch.runtime;

import static io.quarkus.runtime.annotations.ConfigPhase.RUN_TIME;

import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = RUN_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.opensearch")
public interface OpenSearchEmbeddingStoreConfig {

    /**
     * Name of the index that will be used in OpenSearch when searching for related embeddings.
     * If this index doesn't exist, it will be created.
     */
    @WithDefault("default")
    String index();

}
