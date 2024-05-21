package io.quarkiverse.langchain4j.bam.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.langchain4j.model.chat.DisabledChatLanguageModel;
import dev.langchain4j.model.chat.DisabledStreamingChatLanguageModel;
import dev.langchain4j.model.embedding.DisabledEmbeddingModel;
import io.quarkiverse.langchain4j.bam.runtime.config.LangChain4jBamConfig;
import io.quarkiverse.langchain4j.bam.runtime.config.LangChain4jBamConfig.BamConfig;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;

class DisabledModelsBamRecorderTest {

    LangChain4jBamConfig config = mock(LangChain4jBamConfig.class);
    BamConfig defaultConfig = mock(BamConfig.class);
    BamRecorder recorder = new BamRecorder();

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
}
