package io.quarkiverse.langchain4j.watsonx.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.langchain4j.model.chat.DisabledChatModel;
import dev.langchain4j.model.chat.DisabledStreamingChatModel;
import dev.langchain4j.model.embedding.DisabledEmbeddingModel;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkiverse.langchain4j.watsonx.runtime.config.LangChain4jWatsonxConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.config.LangChain4jWatsonxConfig.WatsonConfig;
import io.quarkus.runtime.RuntimeValue;

class DisabledModelsWatsonRecorderTest {
    LangChain4jWatsonxConfig runtimeConfig = mock(LangChain4jWatsonxConfig.class);

    WatsonConfig defaultConfig = mock(WatsonConfig.class);
    WatsonxRecorder recorder = new WatsonxRecorder(new RuntimeValue<>(runtimeConfig));

    @BeforeEach
    void setupMocks() {
        when(defaultConfig.enableIntegration())
                .thenReturn(false);

        when(runtimeConfig.defaultConfig())
                .thenReturn(defaultConfig);
    }

    @Test
    void disabledChatModel() {
        assertThat(recorder
                .generationModel(NamedConfigUtil.DEFAULT_NAME).apply(null))
                .isNotNull()
                .isExactlyInstanceOf(DisabledChatModel.class);

        assertThat(
                recorder.generationStreamingModel(NamedConfigUtil.DEFAULT_NAME).apply(null))
                .isNotNull()
                .isExactlyInstanceOf(DisabledStreamingChatModel.class);

        assertThat(recorder.embeddingModel(NamedConfigUtil.DEFAULT_NAME).get())
                .isNotNull()
                .isExactlyInstanceOf(DisabledEmbeddingModel.class);
    }
}
