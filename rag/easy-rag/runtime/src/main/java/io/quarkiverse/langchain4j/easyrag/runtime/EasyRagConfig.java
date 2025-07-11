package io.quarkiverse.langchain4j.easyrag.runtime;

import static io.quarkus.runtime.annotations.ConfigPhase.RUN_TIME;

import java.util.OptionalDouble;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = RUN_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.easy-rag")
public interface EasyRagConfig {
    enum PathType {
        /**
         * The {@link #path()} represents a filesystem reference
         */
        FILESYSTEM,

        /**
         * The {@link #path()} represents a classpath reference
         */
        CLASSPATH;
    }

    /**
     * Path to the directory containing the documents to be ingested. This is either
     * an absolute or relative path in the filesystem. A relative path is
     * resolved against the current working directory at runtime.
     */
    String path();

    /**
     * Does {@link #path()} represent a filesystem reference or a classpath reference?
     *
     * @see PathType
     */
    @WithDefault("FILESYSTEM")
    PathType pathType();

    /**
     * Matcher used for filtering which files from the directory should be ingested.
     * This uses the {@link java.nio.file.FileSystem} path matcher syntax.
     * Example: `glob:**.txt` to recursively match all files with the `.txt` extension.
     * The default is `glob:**`, recursively matching all files.
     */
    @WithDefault("glob:**")
    String pathMatcher();

    /**
     * Whether to recursively ingest documents from subdirectories.
     */
    @WithDefault("true")
    Boolean recursive();

    /**
     * Maximum segment size when splitting documents, in tokens.
     */
    @WithDefault("300")
    Integer maxSegmentSize();

    /**
     * Maximum overlap (in tokens) when splitting documents.
     */
    @WithDefault("30")
    Integer maxOverlapSize();

    /**
     * Maximum number of results to return when querying the retrieval augmentor.
     */
    @WithDefault("5")
    Integer maxResults();

    /**
     * The minimum score for results to return when querying the retrieval augmentor.
     */
    OptionalDouble minScore();

    /**
     * The strategy to decide whether document ingestion into the store
     * should happen at startup or not. The default is ON. Changing to OFF
     * generally only makes sense if running against a persistent embedding
     * store that was already populated. When set to MANUAL, it is expected
     * that the application will inject and call the {@link
     * io.quarkiverse.langchain4j.easyrag.EasyRagManualIngestion}
     * bean to trigger the ingestion when desired.
     */
    @WithDefault("ON")
    IngestionStrategy ingestionStrategy();

    /**
     * Configuration related to the reusing of embeddings.
     * <p>
     * Currently only supported when using an in-memory embedding store.
     * </p>
     */
    ReuseEmbeddingsConfig reuseEmbeddings();

    @ConfigGroup
    interface ReuseEmbeddingsConfig {
        /**
         * Whether or not to reuse embeddings. Defaults to {@code false}.
         */
        @WithDefault("false")
        boolean enabled();

        /**
         * The file path to load/save embeddings, assuming
         * {@code quarkus.langchain4j.easy-rag.reuse-embeddings.enabled == true}.
         * <p>
         * Defaults to {@code easy-rag-embeddings.json} in the current directory.
         * </p>
         */
        @WithDefault("easy-rag-embeddings.json")
        String file();
    }
}
