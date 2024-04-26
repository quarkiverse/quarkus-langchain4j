package io.quarkiverse.langchain4j.easyrag.runtime;

import static io.quarkus.runtime.annotations.ConfigPhase.RUN_TIME;

import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = RUN_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.easy-rag")
public interface EasyRagConfig {

    /**
     * Path to the directory containing the documents to be ingested.
     */
    String path();

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
     * The strategy to decide whether document ingestion into the store should happen at startup or not.
     * The default is ON. Changing to OFF generally only makes sense if running against a persistent embedding store
     * that was already populated.
     */
    @WithDefault("ON")
    IngestionStrategy ingestionStrategy();

}
