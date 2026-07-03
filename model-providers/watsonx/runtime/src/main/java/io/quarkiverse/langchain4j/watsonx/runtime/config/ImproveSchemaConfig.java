package io.quarkiverse.langchain4j.watsonx.runtime.config;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigGroup;

@ConfigGroup
public interface ImproveSchemaConfig {

    /**
     * Whether improve schema requests should be logged.
     */
    @ConfigDocDefault("false")
    Optional<Boolean> logRequests();

    /**
     * Whether improve schema responses should be logged.
     */
    @ConfigDocDefault("false")
    Optional<Boolean> logResponses();

    /**
     * Whether the watsonx.ai client should log requests as cURL commands.
     */
    @ConfigDocDefault("false")
    Optional<Boolean> logRequestsCurl();
}
