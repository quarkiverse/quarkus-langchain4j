package org.acme.examples.aiservices;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.acme.examples.aiservices.MessageAssertUtils.assertSingleRequestMessage;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.openai.test.WiremockUtils;
import io.quarkus.test.QuarkusUnitTest;

public class MultipleChatModelsDeclarativeServiceTest {

    public static final String MESSAGE_CONTENT = "Tell me a joke about developers";
    private static final int WIREMOCK_PORT = 8089;

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class).addClasses(WiremockUtils.class, MessageAssertUtils.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "defaultKey")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url", "http://localhost:" + WIREMOCK_PORT + "/v1")
            .overrideRuntimeConfigKey("quarkus.langchain4j.model1.openai.api-key", "key1")
            .overrideRuntimeConfigKey("quarkus.langchain4j.model1.openai.base-url", "http://localhost:" + WIREMOCK_PORT + "/v1")
            .overrideRuntimeConfigKey("quarkus.langchain4j.model2.openai.api-key", "key2")
            .overrideRuntimeConfigKey("quarkus.langchain4j.model2.openai.base-url",
                    "http://localhost:" + WIREMOCK_PORT + "/v1");
    private static final TypeReference<Map<String, Object>> MAP_TYPE_REF = new TypeReference<>() {
    };

    static WireMockServer wireMockServer;

    static ObjectMapper mapper;

    @BeforeAll
    static void beforeAll() {
        wireMockServer = new WireMockServer(options().port(WIREMOCK_PORT));
        wireMockServer.start();

        mapper = new ObjectMapper();
    }

    @AfterAll
    static void afterAll() {
        wireMockServer.stop();
    }

    @BeforeEach
    void setup() {
        wireMockServer.resetAll();
    }

    @RegisterAiService
    interface ChatWithDefaultModel {

        String chat(String userMessage);
    }

    @RegisterAiService(modelName = "model1")
    interface ChatWithModel1 {

        String chat(String userMessage);
    }

    @RegisterAiService(modelName = "model2")
    interface ChatWithModel2 {

        String chat(String userMessage);
    }

    @Inject
    ChatWithDefaultModel chatWithDefaultModel;

    @Inject
    ChatWithModel1 chatWithModel1;

    @Inject
    ChatWithModel2 chatWithModel2;

    @Test
    @ActivateRequestContext
    public void testDefaultModel() throws IOException {
        wireMockServer.stubFor(WiremockUtils.chatCompletionsMessageContent(Optional.of("defaultKey"), MESSAGE_CONTENT));
        String result = chatWithDefaultModel.chat(MESSAGE_CONTENT);
        assertThat(result).isNotBlank();

        assertSingleRequestMessage(getRequestAsMap(), MESSAGE_CONTENT);
    }

    @Test
    @ActivateRequestContext
    public void testNamedModel1() throws IOException {
        wireMockServer.stubFor(WiremockUtils.chatCompletionsMessageContent(Optional.of("key1"), MESSAGE_CONTENT));
        String result = chatWithModel1.chat(MESSAGE_CONTENT);
        assertThat(result).isNotBlank();

        assertSingleRequestMessage(getRequestAsMap(), MESSAGE_CONTENT);
    }

    @Test
    @ActivateRequestContext
    public void testNamedModel2() throws IOException {
        wireMockServer.stubFor(WiremockUtils.chatCompletionsMessageContent(Optional.of("key2"), MESSAGE_CONTENT));
        String result = chatWithModel2.chat(MESSAGE_CONTENT);
        assertThat(result).isNotBlank();

        assertSingleRequestMessage(getRequestAsMap(), MESSAGE_CONTENT);
    }

    private Map<String, Object> getRequestAsMap() throws IOException {
        return getRequestAsMap(getRequestBody());
    }

    private Map<String, Object> getRequestAsMap(byte[] body) throws IOException {
        return mapper.readValue(body, MAP_TYPE_REF);
    }

    private byte[] getRequestBody() {
        assertThat(wireMockServer.getAllServeEvents()).hasSize(1);
        ServeEvent serveEvent = wireMockServer.getAllServeEvents().get(0); // this works because we reset requests for Wiremock before each test
        return getRequestBody(serveEvent);
    }

    private byte[] getRequestBody(ServeEvent serveEvent) {
        LoggedRequest request = serveEvent.getRequest();
        assertThat(request.getBody()).isNotEmpty();
        return request.getBody();
    }

}
