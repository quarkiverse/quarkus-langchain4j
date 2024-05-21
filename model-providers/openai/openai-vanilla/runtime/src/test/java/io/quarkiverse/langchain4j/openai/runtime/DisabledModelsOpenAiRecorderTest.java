package io.quarkiverse.langchain4j.openai.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.langchain4j.model.chat.DisabledChatLanguageModel;
import dev.langchain4j.model.chat.DisabledStreamingChatLanguageModel;
import dev.langchain4j.model.embedding.DisabledEmbeddingModel;
import dev.langchain4j.model.image.DisabledImageModel;
import dev.langchain4j.model.moderation.DisabledModerationModel;
import io.quarkiverse.langchain4j.openai.runtime.config.LangChain4jOpenAiConfig;
import io.quarkiverse.langchain4j.openai.runtime.config.LangChain4jOpenAiConfig.OpenAiConfig;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;

class DisabledModelsOpenAiRecorderTest {
    LangChain4jOpenAiConfig config = mock(LangChain4jOpenAiConfig.class);
    OpenAiConfig defaultConfig = mock(OpenAiConfig.class);
    OpenAiRecorder recorder = new OpenAiRecorder();

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

    @Test
    void disabledModerationModel() {
        assertThat(recorder.moderationModel(config, NamedConfigUtil.DEFAULT_NAME).get())
                .isNotNull()
                .isExactlyInstanceOf(DisabledModerationModel.class);
    }
}
