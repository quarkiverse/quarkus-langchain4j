package io.quarkiverse.langchain4j.huggingface.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.langchain4j.model.chat.DisabledChatModel;
import dev.langchain4j.model.embedding.DisabledEmbeddingModel;
import io.quarkiverse.langchain4j.huggingface.runtime.config.LangChain4jHuggingFaceConfig;
import io.quarkiverse.langchain4j.huggingface.runtime.config.LangChain4jHuggingFaceConfig.HuggingFaceConfig;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.runtime.RuntimeValue;

class DisabledModelsHuggingFaceRecorderTest {
    LangChain4jHuggingFaceConfig config = mock(LangChain4jHuggingFaceConfig.class);
    HuggingFaceConfig defaultConfig = mock(HuggingFaceConfig.class);
    HuggingFaceRecorder recorder = new HuggingFaceRecorder(new RuntimeValue<>(config));

    @BeforeEach
    void setupMocks() {
        when(defaultConfig.enableIntegration())
                .thenReturn(false);

        when(config.defaultConfig())
                .thenReturn(defaultConfig);
    }

    @Test
    void disabledChatModel() {
        assertThat(recorder.chatModel(NamedConfigUtil.DEFAULT_NAME).get())
                .isNotNull()
                .isExactlyInstanceOf(DisabledChatModel.class);
    }

    @Test
    void disabledEmbeddingModel() {
        assertThat(recorder.embeddingModel(NamedConfigUtil.DEFAULT_NAME).get())
                .isNotNull()
                .isExactlyInstanceOf(DisabledEmbeddingModel.class);
    }
}
