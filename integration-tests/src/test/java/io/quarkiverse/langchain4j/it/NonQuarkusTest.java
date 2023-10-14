package io.quarkiverse.langchain4j.it;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.Test;

import dev.langchain4j.model.openai.OpenAiChatModel;

public class NonQuarkusTest {

    @Test
    public void testDefaultClientAttemptedToBeUsed() {
        assertThatExceptionOfType(NoClassDefFoundError.class).isThrownBy(() -> OpenAiChatModel.builder().apiKey("test").build())
                .withMessageContaining("okhttp3");
    }
}
