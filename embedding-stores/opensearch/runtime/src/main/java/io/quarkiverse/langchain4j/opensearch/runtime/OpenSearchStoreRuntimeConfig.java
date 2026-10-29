package io.quarkiverse.langchain4j.opensearch.runtime;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface OpenSearchStoreRuntimeConfig {

    /**
     * URL where the OpenSearch server is listening for requests. Required unless an
     * OpenSearchClient has been configured programmatically.
     */
    Optional<String> serverUrl();

    /**
     * The user name used for basic authentication against the OpenSearch server.
     */
    Optional<String> userName();

    /**
     * The password used for basic authentication against the OpenSearch server.
     */
    Optional<String> password();

    /**
     * The API key used to authenticate against Amazon OpenSearch Service.
     */
    Optional<String> apiKey();

    /**
     * The name of the index the embeddings are stored in.
     */
    @WithDefault("default")
    String indexName();
}
