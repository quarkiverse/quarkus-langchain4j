package io.quarkiverse.langchain4j.openai.test;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import dev.langchain4j.model.chat.ChatLanguageModel;
import io.quarkus.test.QuarkusUnitTest;

public class OpenAiChatLanguageModelSmokeTest {

    private static final String TOKEN = "whatever";
    private static final int WIREMOCK_PORT = 8089;

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(WiremockUtils.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", TOKEN)
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url", "http://localhost:" + WIREMOCK_PORT + "/v1");

    static WireMockServer wireMockServer;

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
        wireMockServer.stubFor(WiremockUtils.defaultChatCompletionsStub(TOKEN));
    }

    @Inject
    ChatLanguageModel chatLanguageModel;

    @Test
    void test() {
        String response = chatLanguageModel.generate("hello");
        assertThat(response).isNotBlank();

        assertThat(wireMockServer.getAllServeEvents()).hasSize(1);
        ServeEvent serveEvent = wireMockServer.getAllServeEvents().get(0); // this works because we reset requests for Wiremock before each test
        LoggedRequest loggedRequest = serveEvent.getRequest();
        assertThat(loggedRequest.getHeader("User-Agent")).isEqualTo("Quarkus REST Client");
    }
}
