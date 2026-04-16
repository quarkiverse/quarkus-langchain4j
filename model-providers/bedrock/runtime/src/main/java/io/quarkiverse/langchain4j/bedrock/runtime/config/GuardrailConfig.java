package io.quarkiverse.langchain4j.bedrock.runtime.config;

import java.util.Optional;

import dev.langchain4j.model.bedrock.BedrockGuardrailConfiguration;
import io.quarkus.runtime.annotations.ConfigGroup;

@ConfigGroup
public interface GuardrailConfig {

    /**
     * The unique identifier of the guardrail that you want to use.
     *
     * @see <a href="https://docs.aws.amazon.com/bedrock/latest/userguide/guardrails.html">Amazon Bedrock Guardrails</a>
     */
    Optional<String> guardrailIdentifier();

    /**
     * The version number for the guardrail.
     */
    Optional<String> guardrailVersion();

    /**
     * The processing mode for streaming requests. Possible values are {@code SYNC} or {@code ASYNC}.
     *
     * @see <a href=
     *      "https://docs.aws.amazon.com/bedrock/latest/userguide/guardrails-streaming.html">Configure streaming response
     *      behavior</a>
     */
    Optional<BedrockGuardrailConfiguration.ProcessingMode> streamProcessingMode();
}
