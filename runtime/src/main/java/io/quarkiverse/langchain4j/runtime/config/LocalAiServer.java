package io.quarkiverse.langchain4j.runtime.config;

import java.util.Optional;

public interface LocalAiServer {

    /**
     * Base URL of OpenAI API
     */
    Optional<String> baseUrl();

}
