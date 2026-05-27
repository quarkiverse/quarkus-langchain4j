package io.quarkiverse.langchain4j.runtime.config;

import java.util.Optional;

import io.smallrye.config.WithDefault;

public interface AiServiceConfig {

    int DEFAULT_MAX_TOOL_CALLS_PER_RESPONSE = 0;

    /**
     * Maximum number of tool calls allowed per single LLM response.
     * When an LLM response contains more tool calls than this limit,
     * a ToolCallsLimitExceededException is thrown.
     * The default value is 0, which means unlimited.
     */
    @WithDefault("" + DEFAULT_MAX_TOOL_CALLS_PER_RESPONSE)
    int maxToolCallsPerResponse();

    /**
     * Maximum number of sequential tool executions while handling a single chat request.
     * If this number is exceeded, the chat request will fail.
     *
     * @deprecated Use {@link #maxToolCallingRoundTrips()} instead.
     */
    @Deprecated(forRemoval = true, since = "999-SNAPSHOT")
    Optional<Integer> maxToolExecutions();

    /**
     * Maximum number of LLM request/response round trips while handling a single chat request.
     * If this number is exceeded, the chat request will fail.
     */
    Optional<Integer> maxToolCallingRoundTrips();
}
