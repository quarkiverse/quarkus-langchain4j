package io.quarkiverse.langchain4j.openai.deployment;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface ChatModelBuildConfig {

    /**
     * Whether the model should be enabled
     */
    @ConfigDocDefault("true")
    Optional<Boolean> enabled();

    /**
     * Which OpenAI API the chat model should target.
     * <p>
     * {@code chat-completion} (the default) uses the {@code /v1/chat/completions} endpoint, while {@code responses}
     * uses the newer {@code /v1/responses} endpoint. The {@code responses} mode is required for models that are only
     * available through the Responses API and unlocks Responses-specific settings under
     * {@code quarkus.langchain4j.openai.chat-model.responses.*}.
     */
    @WithDefault("chat-completion")
    Mode mode();

    enum Mode {
        CHAT_COMPLETION,
        RESPONSES
    }
}
