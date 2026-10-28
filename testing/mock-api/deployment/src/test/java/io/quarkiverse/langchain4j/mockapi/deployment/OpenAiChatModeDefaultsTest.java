package io.quarkiverse.langchain4j.mockapi.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkiverse.langchain4j.mockapi.internal.ProviderSpecs;

class OpenAiChatModeDefaultsTest {

    private static final String DEFAULT = "<default>";
    private static final MockApiModelsBuildItem OPENAI_MODELS = new MockApiModelsBuildItem(List.of(ProviderSpecs.OPENAI),
            Map.of(ProviderSpecs.OPENAI.id(), List.of(DEFAULT, "m1")));

    @Test
    void aModelWithoutABaseUrlOfItsOwnTargetsTheResponsesApi() {
        assertThat(MockModelApiProcessor.openAiChatModeDefaults(OPENAI_MODELS, IdentityDefaultValuesTest.config(Map.of())))
                .containsOnly(Map.entry("quarkus.langchain4j.openai.chat-model.mode", "responses"),
                        Map.entry("quarkus.langchain4j.openai.m1.chat-model.mode", "responses"));
    }

    @Test
    void openAisOwnBaseUrlCountsAsNone() {
        assertThat(MockModelApiProcessor.openAiChatModeDefaults(OPENAI_MODELS, IdentityDefaultValuesTest.config(
                Map.of("quarkus.langchain4j.openai.base-url", "https://api.openai.com/v1/",
                        "quarkus.langchain4j.openai.m1.base-url", "https://api.openai.com/v1"))))
                .containsKeys("quarkus.langchain4j.openai.chat-model.mode", "quarkus.langchain4j.openai.m1.chat-model.mode");
    }

    @Test
    void aModelWithAnotherBaseUrlStaysOnChatCompletions() {
        assertThat(MockModelApiProcessor.openAiChatModeDefaults(OPENAI_MODELS, IdentityDefaultValuesTest.config(
                Map.of("quarkus.langchain4j.openai.m1.base-url", "http://localhost:11434/v1"))))
                .containsOnlyKeys("quarkus.langchain4j.openai.chat-model.mode");
    }

    @Test
    void aModeTheUserSetWins() {
        assertThat(MockModelApiProcessor.openAiChatModeDefaults(OPENAI_MODELS, IdentityDefaultValuesTest.config(
                Map.of("quarkus.langchain4j.openai.chat-model.mode", "chat-completion"))))
                .containsOnlyKeys("quarkus.langchain4j.openai.m1.chat-model.mode");
    }

    @Test
    void otherProvidersGetNothing() {
        MockApiModelsBuildItem models = new MockApiModelsBuildItem(List.of(ProviderSpecs.MISTRAL_AI),
                Map.of(ProviderSpecs.MISTRAL_AI.id(), List.of(DEFAULT)));
        assertThat(MockModelApiProcessor.openAiChatModeDefaults(models, IdentityDefaultValuesTest.config(Map.of()))).isEmpty();
    }
}
