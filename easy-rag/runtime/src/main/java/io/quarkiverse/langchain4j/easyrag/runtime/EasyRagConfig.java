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

}
