package io.quarkiverse.langchain4j.openai.runtime.config;

import java.util.List;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;

/**
 * Settings that only apply when {@code quarkus.langchain4j.openai.chat-model.mode} is set to {@code responses},
 * i.e. when the chat model targets the OpenAI {@code /v1/responses} endpoint.
 */
@ConfigGroup
public interface ResponsesChatModelConfig {

    /**
     * Whether to store the generated model response for later retrieval via the Responses API.
     */
    Optional<Boolean> store();

    /**
     * The unique ID of the previous response to the model, used to create multi-turn conversations.
     */
    Optional<String> previousResponseId();

    /**
     * A summary of the reasoning performed by the model, for reasoning models.
     * Supported values are {@code auto}, {@code concise}, and {@code detailed}.
     */
    Optional<String> reasoningSummary();

    /**
     * An upper bound for the number of tokens that can be generated for a response, including visible output tokens
     * and reasoning tokens.
     */
    Optional<Integer> maxOutputTokens();

    /**
     * The maximum number of total calls to built-in tools that can be processed in a response.
     */
    Optional<Integer> maxToolCalls();

    /**
     * Whether to allow the model to run tool calls in parallel.
     */
    Optional<Boolean> parallelToolCalls();

    /**
     * Additional output data to include in the model response.
     */
    Optional<List<String>> include();

    /**
     * The truncation strategy to use for the model response. Supported values are {@code auto} and {@code disabled}.
     */
    Optional<String> truncation();

    /**
     * Constrains the verbosity of the model's response. Supported values are {@code low}, {@code medium}, and
     * {@code high}.
     */
    Optional<String> textVerbosity();

    /**
     * Used by OpenAI to cache responses for similar requests to optimize your cache hit rates.
     */
    Optional<String> promptCacheKey();

    /**
     * The retention policy for the prompt cache.
     */
    Optional<String> promptCacheRetention();

    /**
     * A stable identifier used to help detect users of your application that may be violating OpenAI's usage policies.
     */
    Optional<String> safetyIdentifier();

    /**
     * The number of most likely tokens to return at each token position, each with an associated log probability.
     */
    Optional<Integer> topLogprobs();

    /**
     * Whether to enforce strict schema validation on tool/function definitions.
     */
    Optional<Boolean> strictTools();

    /**
     * Whether obfuscation fields should be included in streaming events. Only applies to the streaming chat model.
     */
    Optional<Boolean> streamIncludeObfuscation();
}
