package io.quarkiverse.langchain4j.watsonx.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.langchain4j.model.chat.DisabledChatLanguageModel;
import dev.langchain4j.model.chat.DisabledStreamingChatLanguageModel;
import io.quarkiverse.langchain4j.runtime.NamedModelUtil;
import io.quarkiverse.langchain4j.watsonx.runtime.config.LangChain4jWatsonxConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.config.LangChain4jWatsonxConfig.WatsonConfig;

class DisabledModelsWatsonRecorderTest {
    LangChain4jWatsonxConfig config = mock(LangChain4jWatsonxConfig.class);
    WatsonConfig defaultConfig = mock(WatsonConfig.class);
    WatsonxRecorder recorder = new WatsonxRecorder();

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

        assertThat(recorder.streamingChatModel(config, NamedModelUtil.DEFAULT_NAME).get())
                .isNotNull()
                .isExactlyInstanceOf(DisabledStreamingChatLanguageModel.class);
    }
}
