package io.quarkiverse.langchain4j.llama.parse;

import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import static io.quarkus.runtime.annotations.ConfigPhase.BUILD_TIME;

@ConfigRoot(phase = BUILD_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.llamaparser")
public interface LlamaParseBuildConfig {

    String apiKey();

    /**
     * Base URL of OpenAI API
     */
    @WithDefault("https://api.cloud.llamaindex.ai/api/parsing")
    String baseUrl();
}
