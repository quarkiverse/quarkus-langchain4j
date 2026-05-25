package io.quarkiverse.langchain4j.infinispan;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;

@ConfigGroup
public interface InfinispanNamedStoreBuildTimeConfig {

    /**
     * The dimension of the embedding vectors for this named store.
     */
    Optional<Long> dimension();

    /**
     * The name of the Infinispan client to use, as configured via the {@code infinispan-client}
     * extension. If unspecified the default Infinispan client is used.
     */
    Optional<String> clientName();
}
