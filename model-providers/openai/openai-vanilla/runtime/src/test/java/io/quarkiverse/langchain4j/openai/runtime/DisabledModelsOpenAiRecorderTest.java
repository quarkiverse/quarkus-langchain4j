package io.quarkiverse.langchain4j.openai.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.DisabledChatModel;
import dev.langchain4j.model.chat.DisabledStreamingChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.DisabledEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.image.DisabledImageModel;
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.model.moderation.DisabledModerationModel;
import dev.langchain4j.model.moderation.ModerationModel;
import io.quarkiverse.langchain4j.openai.runtime.config.LangChain4jOpenAiConfig;
import io.quarkiverse.langchain4j.openai.runtime.config.LangChain4jOpenAiConfig.OpenAiConfig;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.RuntimeValue;

class DisabledModelsOpenAiRecorderTest {
    LangChain4jOpenAiConfig config = mock(LangChain4jOpenAiConfig.class);
    OpenAiConfig defaultConfig = mock(OpenAiConfig.class);
    OpenAiRecorder recorder = new OpenAiRecorder(new RuntimeValue<>(config));

    @BeforeEach
    void setupMocks() {
        when(defaultConfig.enableIntegration())
                .thenReturn(false);

        when(config.defaultConfig())
                .thenReturn(defaultConfig);
    }

    @Test
    void disabledChatModel() {
        SyntheticCreationalContext<ChatModel> mock = mock(SyntheticCreationalContext.class);

        assertThat(recorder.chatModel(NamedConfigUtil.DEFAULT_NAME).apply(mock))
                .isNotNull()
                .isExactlyInstanceOf(DisabledChatModel.class);
    }

    @Test
    void disabledStreamingChatModel() {
        SyntheticCreationalContext<StreamingChatModel> mock = mock(SyntheticCreationalContext.class);
        assertThat(recorder.streamingChatModel(NamedConfigUtil.DEFAULT_NAME).apply(mock))
                .isNotNull()
                .isExactlyInstanceOf(DisabledStreamingChatModel.class);
    }

    @Test
    void disabledEmbeddingModel() {
        SyntheticCreationalContext<EmbeddingModel> mock = mock(SyntheticCreationalContext.class);
        assertThat(recorder.embeddingModel(NamedConfigUtil.DEFAULT_NAME).apply(mock))
                .isNotNull()
                .isExactlyInstanceOf(DisabledEmbeddingModel.class);
    }

    @Test
    void disabledImageModel() {
        SyntheticCreationalContext<ImageModel> mock = mock(SyntheticCreationalContext.class);
        assertThat(recorder.imageModel(NamedConfigUtil.DEFAULT_NAME).apply(mock))
                .isNotNull()
                .isExactlyInstanceOf(DisabledImageModel.class);
    }

    @Test
    void disabledModerationModel() {
        SyntheticCreationalContext<ModerationModel> mock = mock(SyntheticCreationalContext.class);
        assertThat(recorder.moderationModel(NamedConfigUtil.DEFAULT_NAME).apply(mock))
                .isNotNull()
                .isExactlyInstanceOf(DisabledModerationModel.class);
    }
}
