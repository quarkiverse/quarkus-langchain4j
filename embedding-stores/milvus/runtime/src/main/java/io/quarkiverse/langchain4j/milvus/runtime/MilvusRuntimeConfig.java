package io.quarkiverse.langchain4j.milvus.runtime;

import static io.quarkus.runtime.annotations.ConfigPhase.RUN_TIME;

import java.time.Duration;
import java.util.Optional;

import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = RUN_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.milvus")
public interface MilvusRuntimeConfig {

    /**
     * The URL of the Milvus server.
     */
    String host();

    /**
     * The port of the Milvus server.
     */
    Integer port();

    /**
     * The authentication token for the Milvus server.
     */
    Optional<String> token();

    /**
     * The username for the Milvus server.
     */
    Optional<String> username();

    /**
     * The password for the Milvus server.
     */
    Optional<String> password();

    /**
     * The timeout duration for the Milvus client. If not specified, 5 seconds will be used.
     */
    Optional<Duration> timeout();

    /**
     * Name of the database.
     */
    @WithDefault("default")
    String dbName();

    /**
     * Create the collection if it does not exist yet.
     */
    @WithDefault("true")
    boolean createCollection();

    /**
     * Name of the collection.
     */
    @WithDefault("embeddings")
    String collectionName();

    /**
     * Dimension of the vectors. Only applicable when the collection yet has to be created.
     */
    Optional<Integer> dimension();

    /**
     * Name of the field that contains the ID of the vector.
     */
    @WithDefault("id")
    String primaryField();

    /**
     * Name of the field that contains the text from which the vector was calculated.
     */
    @WithDefault("text")
    String textField();

    /**
     * Name of the field that contains JSON metadata associated with the text.
     */
    @WithDefault("metadata")
    String metadataField();

    /**
     * Name of the field to store the vector in.
     */
    @WithDefault("vector")
    String vectorField();

    /**
     * Description of the collection.
     */
    Optional<String> description();

    /**
     * The index type to use for the collection.
     */
    @WithDefault("FLAT")
    IndexType indexType();

    /**
     * The metric type to use for searching.
     */
    @WithDefault("COSINE")
    MetricType metricType();

    /**
     * The consistency level.
     */
    @WithDefault("EVENTUALLY")
    ConsistencyLevelEnum consistencyLevel();

}
