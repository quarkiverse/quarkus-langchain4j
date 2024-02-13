package io.quarkiverse.langchain4j.ollama.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.langchain4j.model.chat.DisabledChatLanguageModel;
import dev.langchain4j.model.embedding.DisabledEmbeddingModel;
import io.quarkiverse.langchain4j.ollama.runtime.config.Langchain4jOllamaConfig;
import io.quarkiverse.langchain4j.ollama.runtime.config.Langchain4jOllamaConfig.OllamaConfig;
import io.quarkiverse.langchain4j.runtime.NamedModelUtil;

class DisabledModelsOllamaRecorderTest {
    Langchain4jOllamaConfig config = mock(Langchain4jOllamaConfig.class);
    OllamaConfig defaultConfig = mock(OllamaConfig.class);
    OllamaRecorder recorder = new OllamaRecorder();

    @BeforeEach
    void setupMocks() {
        when(defaultConfig.enableIntegration())
            .thenReturn(false);

        when(config.defaultConfig())
            .thenReturn(defaultConfig);
    }

    @Test
    void disabledChatModel() {
        assertThat(recorder.chatModel(config, NamedModelUtil.DEFAULT_NAME).get())
                .isNotNull()
                .isExactlyInstanceOf(DisabledChatLanguageModel.class);
    }

    @Test
    void disabledEmbeddingModel() {
        assertThat(recorder.embeddingModel(config, NamedModelUtil.DEFAULT_NAME).get())
                .isNotNull()
                .isExactlyInstanceOf(DisabledEmbeddingModel.class);
    }
}