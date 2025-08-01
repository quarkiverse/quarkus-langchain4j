package io.quarkiverse.langchain4j.azure.openai.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.langchain4j.model.chat.DisabledChatModel;
import dev.langchain4j.model.chat.DisabledStreamingChatModel;
import dev.langchain4j.model.embedding.DisabledEmbeddingModel;
import dev.langchain4j.model.image.DisabledImageModel;
import io.quarkiverse.langchain4j.azure.openai.runtime.config.LangChain4jAzureOpenAiConfig;
import io.quarkiverse.langchain4j.azure.openai.runtime.config.LangChain4jAzureOpenAiConfig.AzureAiConfig;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.runtime.RuntimeValue;

class DisabledModelsAzureOpenAiRecorderTest {
    LangChain4jAzureOpenAiConfig config = mock(LangChain4jAzureOpenAiConfig.class);
    AzureAiConfig defaultConfig = mock(AzureAiConfig.class);
    AzureOpenAiRecorder recorder = new AzureOpenAiRecorder(new RuntimeValue<>(config));

    @BeforeEach
    void setupMocks() {
        when(defaultConfig.enableIntegration())
                .thenReturn(false);

        when(config.defaultConfig())
                .thenReturn(defaultConfig);
    }

    @Test
    void disabledChatModel() {
        assertThat(recorder.chatModel(NamedConfigUtil.DEFAULT_NAME).apply(null))
                .isNotNull()
                .isExactlyInstanceOf(DisabledChatModel.class);
    }

    @Test
    void disabledStreamingChatModel() {
        assertThat(recorder.streamingChatModel(NamedConfigUtil.DEFAULT_NAME).apply(null))
                .isNotNull()
                .isExactlyInstanceOf(DisabledStreamingChatModel.class);
    }

    @Test
    void disabledEmbeddingModel() {
        assertThat(recorder.embeddingModel(NamedConfigUtil.DEFAULT_NAME).apply(null))
                .isNotNull()
                .isExactlyInstanceOf(DisabledEmbeddingModel.class);
    }

    @Test
    void disabledImageModel() {
        assertThat(recorder.imageModel(NamedConfigUtil.DEFAULT_NAME).apply(null))
                .isNotNull()
                .isExactlyInstanceOf(DisabledImageModel.class);
    }
}
