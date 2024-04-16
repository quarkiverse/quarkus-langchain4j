package io.quarkiverse.langchain4j.anthropic.deployment;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import com.github.tomakehurst.wiremock.WireMockServer;

abstract class AnthropicSmokeTest {
    protected static final int WIREMOCK_PORT = 8089;
    protected static final String CHAT_MODEL_ID = "claude-3-haiku-20240307";
    protected static final String API_KEY = "somekey";

    protected static WireMockServer wireMockServer;

    @BeforeAll
    static void beforeAll() {
        wireMockServer = new WireMockServer(options().port(WIREMOCK_PORT));
        wireMockServer.start();
    }

    @AfterAll
    static void afterAll() {
        wireMockServer.stop();
    }

    @BeforeEach
    void setup() {
        wireMockServer.resetAll();
    }
}
