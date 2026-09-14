package io.quarkiverse.langchain4j.anthropic;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class QuarkusAnthropicClientTest {

    @Test
    void defaultsTimeoutWhenBuilderDoesNotSetOne() {
        assertThat(QuarkusAnthropicClient.timeoutOrDefault(null)).isEqualTo(Duration.ofSeconds(15));
    }

    @Test
    void preservesExplicitTimeout() {
        Duration timeout = Duration.ofSeconds(30);

        assertThat(QuarkusAnthropicClient.timeoutOrDefault(timeout)).isSameAs(timeout);
    }
}
