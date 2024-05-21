package io.quarkiverse.langchain4j.huggingface.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.langchain4j.model.chat.DisabledChatLanguageModel;
import dev.langchain4j.model.embedding.DisabledEmbeddingModel;
import io.quarkiverse.langchain4j.huggingface.runtime.config.LangChain4jHuggingFaceConfig;
import io.quarkiverse.langchain4j.huggingface.runtime.config.LangChain4jHuggingFaceConfig.HuggingFaceConfig;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;

class DisabledModelsHuggingFaceRecorderTest {
    LangChain4jHuggingFaceConfig config = mock(LangChain4jHuggingFaceConfig.class);
    HuggingFaceConfig defaultConfig = mock(HuggingFaceConfig.class);
    HuggingFaceRecorder recorder = new HuggingFaceRecorder();

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
    void disabledEmbeddingModel() {
        assertThat(recorder.embeddingModel(config, NamedConfigUtil.DEFAULT_NAME).get())
                .isNotNull()
                .isExactlyInstanceOf(DisabledEmbeddingModel.class);
    }
}
