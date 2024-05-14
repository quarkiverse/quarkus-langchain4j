package io.quarkiverse.langchain4j.azure.openai.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.langchain4j.model.chat.DisabledChatLanguageModel;
import dev.langchain4j.model.chat.DisabledStreamingChatLanguageModel;
import dev.langchain4j.model.embedding.DisabledEmbeddingModel;
import dev.langchain4j.model.image.DisabledImageModel;
import io.quarkiverse.langchain4j.azure.openai.runtime.config.LangChain4jAzureOpenAiConfig;
import io.quarkiverse.langchain4j.azure.openai.runtime.config.LangChain4jAzureOpenAiConfig.AzureAiConfig;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;

class DisabledModelsAzureOpenAiRecorderTest {
    LangChain4jAzureOpenAiConfig config = mock(LangChain4jAzureOpenAiConfig.class);
    AzureAiConfig defaultConfig = mock(AzureAiConfig.class);
    AzureOpenAiRecorder recorder = new AzureOpenAiRecorder();

    @BeforeEach
    void setupMocks() {
        when(defaultConfig.enableIntegration())
            .thenReturn(false);

        when(config.defaultConfig())
            .thenReturn(defaultConfig);
    }

    @Test
    void disabledChatModel() {
        assertThat(recorder.chatModel(config, NamedConfigUtil.DEFAULT_NAME).get())
                .isNotNull()
                .isExactlyInstanceOf(DisabledChatLanguageModel.class);
    }

    @Test
    void disabledStreamingChatModel() {
        assertThat(recorder.streamingChatModel(config, NamedConfigUtil.DEFAULT_NAME).get())
                .isNotNull()
                .isExactlyInstanceOf(DisabledStreamingChatLanguageModel.class);
    }

    @Test
    void disabledEmbeddingModel() {
        assertThat(recorder.embeddingModel(config, NamedConfigUtil.DEFAULT_NAME).get())
                .isNotNull()
                .isExactlyInstanceOf(DisabledEmbeddingModel.class);
    }

    @Test
    void disabledImageModel() {
        assertThat(recorder.imageModel(config, NamedConfigUtil.DEFAULT_NAME).get())
                .isNotNull()
                .isExactlyInstanceOf(DisabledImageModel.class);
    }
}
