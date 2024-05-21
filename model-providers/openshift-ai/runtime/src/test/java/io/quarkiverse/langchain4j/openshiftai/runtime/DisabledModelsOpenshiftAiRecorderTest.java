package io.quarkiverse.langchain4j.openshiftai.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.langchain4j.model.chat.DisabledChatLanguageModel;
import io.quarkiverse.langchain4j.openshiftai.runtime.config.LangChain4jOpenshiftAiConfig;
import io.quarkiverse.langchain4j.openshiftai.runtime.config.LangChain4jOpenshiftAiConfig.OpenshiftAiConfig;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;

class DisabledModelsOpenshiftAiRecorderTest {
    LangChain4jOpenshiftAiConfig config = mock(LangChain4jOpenshiftAiConfig.class);
    OpenshiftAiConfig defaultConfig = mock(OpenshiftAiConfig.class);
    OpenshiftAiRecorder recorder = new OpenshiftAiRecorder();

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
}
