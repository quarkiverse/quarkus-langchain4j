package io.quarkiverse.langchain4j.chroma.runtime;

import static io.quarkus.runtime.annotations.ConfigPhase.RUN_TIME;

import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;

@ConfigRoot(phase = RUN_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.chroma")
public interface ChromaConfig {

    /**
     * URL where the Chroma database is listening for requests
     */
    String url();
}
