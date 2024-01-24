package io.quarkiverse.langchain4j.pinecone.runtime;

import static io.quarkus.runtime.annotations.ConfigPhase.RUN_TIME;

import java.time.Duration;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = RUN_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.pinecone")
public interface PineconeConfig {

    /**
     * The API key to Pinecone.
     */
    String apiKey();

    /**
     * Environment name, e.g. gcp-starter or northamerica-northeast1-gcp.
     */
    String environment();

    /**
     * ID of the project.
     */
    String projectId();

    /**
     * Name of the index within the project. If the index doesn't exist, it will be created.
     */
    String indexName();

    /**
     * Dimension of the embeddings in the index. This is required only in case that the index doesn't exist yet
     * and needs to be created.
     */
    Optional<Integer> dimension();

    /**
     * The type of the pod to use. This is only used if the index doesn't exist yet and needs to be created.
     * The format: One of `s1`, `p1`, or `p2` appended with `.` and one of `x1`, `x2`, `x4`, or `x8`.
     */
    @WithDefault("s1.x1")
    String podType();

    /**
     * The timeout duration for the index to become ready. Only relevant if the index doesn't exist yet and needs to be
     * created. If not specified, 1 minute will be used.
     */
    Optional<Duration> indexReadinessTimeout();

    /**
     * The namespace.
     */
    Optional<String> namespace();

    /**
     * The name of the field that contains the text segment.
     */
    @WithDefault("text")
    String textFieldName();

    /**
     * The timeout duration for the Pinecone client. If not specified, 5 seconds will be used.
     */
    Optional<Duration> timeout();

}
