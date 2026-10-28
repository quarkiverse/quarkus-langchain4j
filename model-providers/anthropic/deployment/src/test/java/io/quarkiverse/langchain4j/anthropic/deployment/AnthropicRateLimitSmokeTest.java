package io.quarkiverse.langchain4j.anthropic.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.util.Map;
import java.util.Optional;

import jakarta.inject.Inject;

import org.assertj.core.util.Throwables;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.QuarkusHttpException;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkiverse.langchain4j.mockapi.RawResponse;
import io.quarkus.test.QuarkusExtensionTest;

class AnthropicRateLimitSmokeTest extends AnthropicSmokeTest {
    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.anthropic.api-key", API_KEY);

    @Inject
    ChatModel chatModel;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void reset() {
        mock.reset();
    }

    @Test
    void rateLimitExposesResponseHeaders() {
        // a raw stub: the neutral error cannot carry the Retry-After header
        mock.raw().on("POST", "/anthropic/v1/messages")
                .replies(RawResponse.of(429, Map.of("Content-Type", "application/json", "Retry-After", "7"), """
                        {
                          "type": "error",
                          "error": {
                            "type": "rate_limit_error",
                            "message": "Number of request tokens has exceeded your per-minute rate limit"
                          }
                        }
                        """));

        Throwable thrown = catchThrowable(() -> chatModel.chat("Hello, how are you today?"));

        assertThat(thrown).isInstanceOf(RateLimitException.class);
        assertThat(thrown).getRootCause().isInstanceOf(QuarkusHttpException.class);
        QuarkusHttpException cause = (QuarkusHttpException) Throwables.getRootCause(thrown);
        assertThat(cause.header("Retry-After")).isEqualTo(Optional.of("7"));
        assertThat(mock.requests())
                .isNotEmpty()
                .allSatisfy(request -> assertThat(request.header("x-api-key")).isEqualTo(API_KEY));
    }
}
