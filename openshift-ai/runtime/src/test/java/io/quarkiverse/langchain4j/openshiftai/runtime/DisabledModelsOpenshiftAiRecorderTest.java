package io.quarkiverse.langchain4j.openshiftai.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.langchain4j.model.chat.DisabledChatLanguageModel;
import io.quarkiverse.langchain4j.openshiftai.runtime.config.Langchain4jOpenshiftAiConfig;
import io.quarkiverse.langchain4j.openshiftai.runtime.config.Langchain4jOpenshiftAiConfig.OpenshiftAiConfig;
import io.quarkiverse.langchain4j.runtime.NamedModelUtil;

class DisabledModelsOpenshiftAiRecorderTest {
    Langchain4jOpenshiftAiConfig config = mock(Langchain4jOpenshiftAiConfig.class);
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
        assertThat(recorder.chatModel(config, NamedModelUtil.DEFAULT_NAME).get())
                .isNotNull()
                .isExactlyInstanceOf(DisabledChatLanguageModel.class);
    }
}