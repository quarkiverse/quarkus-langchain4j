package io.quarkiverse.langchain4j.milvus;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface MilvusNamedStoreBuildTimeConfig {

    /**
     * The host of the Milvus server to use for this named store.
     * If set to {@code <default>}, the default Milvus server will be used.
     */
    @WithDefault("<default>")
    Optional<String> host();
}
