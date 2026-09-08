package io.quarkiverse.langchain4j.anthropic;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class QuarkusAnthropicClientTest {

    @Test
    void usesDefaultTimeoutWhenItIsNotConfigured() {
        assertThat(QuarkusAnthropicClient.resolveTimeout(null))
                .isEqualTo(Duration.ofSeconds(15));
    }

    @Test
    void keepsConfiguredTimeout() {
        Duration timeout = Duration.ofSeconds(30);

        assertThat(QuarkusAnthropicClient.resolveTimeout(timeout))
                .isSameAs(timeout);
    }
}
