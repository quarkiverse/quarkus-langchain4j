package io.quarkiverse.langchain4j.infinispan;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface InfinispanNamedStoreBuildTimeConfig {

    /**
     * The name of the Infinispan client to use. These clients are configured by means of the `infinispan-client` extension.
     * If unspecified, it will use the default Infinispan client.
     */
    @WithDefault("<default>")
    Optional<String> clientName();
}
