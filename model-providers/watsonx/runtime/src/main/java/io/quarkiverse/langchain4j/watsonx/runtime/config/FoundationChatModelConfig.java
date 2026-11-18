package io.quarkiverse.langchain4j.watsonx.runtime.config;

import java.util.Optional;
import java.util.Set;

import com.ibm.watsonx.ai.chat.model.ThinkingEffort;

import io.quarkus.runtime.annotations.ConfigGroup;

public interface FoundationChatModelConfig extends CommonChatModelConfig {

    /**
     * Specifies a set of allowed output choices.
     * <p>
     * When this parameter is set, the model is constrained to return exactly one of the provided choices.
     */
    Optional<Set<String>> guidedChoice();

    /**
     * Constrains the model output to follow a context-free grammar.
     * <p>
     * If specified, the generated output will conform to the defined grammar.
     */
    Optional<String> guidedGrammar();

    /**
     * Constrains the model output to match a regular expression pattern.
     * <p>
     * If specified, the generated output must conform to the provided regex.
     */
    Optional<String> guidedRegex();

    /**
     * Configuration to enable and customize the reasoning part of model responses.
     * <p>
     * The appropriate configuration depends on the model’s output format:
     * <ul>
     * <li><b>ExtractionTags</b> — for models that return reasoning and response together in a single text block, enclosed in
     * XML-like tags (e.g.
     * {@code ibm/granite-3-3-8b-instruct}).</li>
     * <li><b>ThinkingEffort</b> — for models that return reasoning and response separately, allowing control over how much
     * reasoning the model
     * performs during generation (e.g. {@code openai/gpt-oss-120b}).</li>
     * </ul>
     */
    Optional<ThinkingConfig> thinking();

    @ConfigGroup
    public interface ThinkingConfig {

        /**
         * Enables or disables reasoning.
         */
        Optional<Boolean> enabled();

        /**
         * Defines the extraction tags used when the model returns reasoning and response in the same text block.
         * <p>
         * Example for models like {@code ibm/granite-3-3-8b-instruct}:
         *
         * <pre>{@code
         * quarkus.langchain4j.thinking.tags.think=think
         * quarkus.langchain4j.thinking.tags.response=response
         * }</pre>
         */
        Optional<ExtractionTagsConfig> tags();

        /**
         * Controls the reasoning effort level for models that separate reasoning and response automatically.
         * <p>
         * Example values: {@code LOW}, {@code MEDIUM}, {@code HIGH}.
         */
        Optional<ThinkingEffort> effort();

        /**
         * Determines whether the reasoning portion returned by the model should be included in the final response provided to
         * the application.
         */
        Optional<Boolean> includeReasoning();
    }

    @ConfigGroup
    public interface ExtractionTagsConfig {

        /**
         * The XML-like tag enclosing the model’s internal reasoning.
         * <p>
         * Example: {@code <think> ... </think>}
         */
        Think think();

        /**
         * The XML-like tag enclosing the model’s final response.
         * <p>
         * Optional — if not defined, all text outside the reasoning tag is treated as the response.
         */
        Optional<Response> response();
    }

    @ConfigGroup
    public interface Think {

        /**
         * The opening delimiter for the model's internal reasoning section.
         */
        String opening();

        /**
         * The closing delimiter for the model's internal reasoning section.
         */
        String closing();
    }

    @ConfigGroup
    public interface Response {

        /**
         * The opening delimiter for the model's final response section.
         */
        String opening();

        /**
         * The closing delimiter for the model's final response section.
         */
        String closing();
    }
}
