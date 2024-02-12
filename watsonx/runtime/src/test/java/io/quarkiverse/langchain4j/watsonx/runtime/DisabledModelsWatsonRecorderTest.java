package io.quarkiverse.langchain4j.watsonx.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.langchain4j.model.chat.DisabledChatLanguageModel;
import io.quarkiverse.langchain4j.runtime.NamedModelUtil;
import io.quarkiverse.langchain4j.watsonx.runtime.config.Langchain4jWatsonConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.config.Langchain4jWatsonConfig.WatsonConfig;

class DisabledModelsWatsonRecorderTest {
    Langchain4jWatsonConfig config = mock(Langchain4jWatsonConfig.class);
    WatsonConfig defaultConfig = mock(WatsonConfig.class);
    WatsonRecorder recorder = new WatsonRecorder();

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
}