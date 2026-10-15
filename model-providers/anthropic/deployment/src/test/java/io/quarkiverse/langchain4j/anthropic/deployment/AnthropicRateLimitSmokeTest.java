package io.quarkiverse.langchain4j.anthropic.deployment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.util.Optional;

import jakarta.inject.Inject;

import org.assertj.core.util.Throwables;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.QuarkusHttpException;
import io.quarkus.test.QuarkusUnitTest;

class AnthropicRateLimitSmokeTest extends AnthropicSmokeTest {
    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.anthropic.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.anthropic.base-url", "http://localhost:%d".formatted(WIREMOCK_PORT));

    @Inject
    ChatModel chatModel;

    @Test
    void rateLimitExposesResponseHeaders() {
        wireMockServer.stubFor(
                post(urlPathEqualTo("/messages"))
                        .withHeader("x-api-key", equalTo(API_KEY))
                        .willReturn(aResponse()
                                .withStatus(429)
                                .withHeader("Content-Type", "application/json")
                                .withHeader("Retry-After", "7")
                                .withBody("""
                                        {
                                          "type": "error",
                                          "error": {
                                            "type": "rate_limit_error",
                                            "message": "Number of request tokens has exceeded your per-minute rate limit"
                                          }
                                        }
                                        """)));

        Throwable thrown = catchThrowable(() -> chatModel.chat("Hello, how are you today?"));

        assertThat(thrown).isInstanceOf(RateLimitException.class);
        assertThat(thrown).getRootCause().isInstanceOf(QuarkusHttpException.class);
        QuarkusHttpException cause = (QuarkusHttpException) Throwables.getRootCause(thrown);
        assertThat(cause.header("Retry-After")).isEqualTo(Optional.of("7"));
    }
}
