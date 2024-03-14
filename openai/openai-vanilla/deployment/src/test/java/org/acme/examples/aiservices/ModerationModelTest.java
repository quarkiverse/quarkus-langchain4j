package org.acme.examples.aiservices;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static io.quarkiverse.langchain4j.openai.test.WiremockUtils.DEFAULT_TOKEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

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

import dev.langchain4j.service.Moderate;
import dev.langchain4j.service.ModerationException;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.openai.test.WiremockUtils;
import io.quarkus.test.QuarkusUnitTest;

public class ModerationModelTest {

    private static final int WIREMOCK_PORT = 8089;

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class).addClasses(WiremockUtils.class, MessageAssertUtils.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url", "http://localhost:" + WIREMOCK_PORT + "/v1");
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
        wireMockServer.stubFor(WiremockUtils.defaultChatCompletionsStub());
    }

    @RegisterAiService(moderationModelSupplier = RegisterAiService.BeanIfExistsModerationModelSupplier.class)
    interface ChatWithModeration {

        @Moderate
        String chat(String message);
    }

    @Inject
    ChatWithModeration chatWithModeration;

    @Test
    @ActivateRequestContext
    void should_throw_when_text_is_flagged() {
        wireMockServer.stubFor(WiremockUtils.moderationMapping(DEFAULT_TOKEN)
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                                """
                                        {
                                            "id": "modr-8Bmx2bYNsgzuAsSuxaQRDCMKHgJbC",
                                            "model": "text-moderation-006",
                                            "results": [
                                                {
                                                    "flagged": true,
                                                    "categories": {
                                                        "sexual": false,
                                                        "hate": true,
                                                        "harassment": false,
                                                        "self-harm": false,
                                                        "sexual/minors": false,
                                                        "hate/threatening": true,
                                                        "violence/graphic": false,
                                                        "self-harm/intent": false,
                                                        "self-harm/instructions": false,
                                                        "harassment/threatening": false,
                                                        "violence": false
                                                    },
                                                    "category_scores": {
                                                        "sexual": 0.0001485530665377155,
                                                        "hate": 0.00004570276360027492,
                                                        "harassment": 0.00006113418203312904,
                                                        "self-harm": 5.4490744361146426e-8,
                                                        "sexual/minors": 6.557503979820467e-7,
                                                        "hate/threatening": 7.536454127432535e-9,
                                                        "violence/graphic": 2.776141343474592e-7,
                                                        "self-harm/intent": 9.653235544249128e-9,
                                                        "self-harm/instructions": 1.2119762970996817e-9,
                                                        "harassment/threatening": 5.06949959344638e-7,
                                                        "violence": 0.000026839805286726914
                                                    }
                                                }
                                            ]
                                        }
                                                                                """)));

        assertThatThrownBy(() -> chatWithModeration.chat("I WILL KILL YOU!!!"))
                .isExactlyInstanceOf(ModerationException.class)
                .hasMessage("Text \"" + "I WILL KILL YOU!!!" + "\" violates content policy");
    }

    @Test
    @ActivateRequestContext
    void should_not_throw_when_text_is_not_flagged() {
        wireMockServer.stubFor(WiremockUtils.moderationMapping(DEFAULT_TOKEN)
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                                """
                                        {
                                            "id": "modr-8Bmx2bYNsgzuAsSuxaQRDCMKHgJbC",
                                            "model": "text-moderation-006",
                                            "results": [
                                                {
                                                    "flagged": false,
                                                    "categories": {
                                                        "sexual": false,
                                                        "hate": true,
                                                        "harassment": false,
                                                        "self-harm": false,
                                                        "sexual/minors": false,
                                                        "hate/threatening": false,
                                                        "violence/graphic": false,
                                                        "self-harm/intent": false,
                                                        "self-harm/instructions": false,
                                                        "harassment/threatening": false,
                                                        "violence": false
                                                    },
                                                    "category_scores": {
                                                        "sexual": 0.0001485530665377155,
                                                        "hate": 0.00004570276360027492,
                                                        "harassment": 0.00006113418203312904,
                                                        "self-harm": 5.4490744361146426e-8,
                                                        "sexual/minors": 6.557503979820467e-7,
                                                        "hate/threatening": 7.536454127432535e-9,
                                                        "violence/graphic": 2.776141343474592e-7,
                                                        "self-harm/intent": 9.653235544249128e-9,
                                                        "self-harm/instructions": 1.2119762970996817e-9,
                                                        "harassment/threatening": 5.06949959344638e-7,
                                                        "violence": 0.000026839805286726914
                                                    }
                                                }
                                            ]
                                        }
                                                                                """)));

        String result = chatWithModeration.chat("I will hug you");
        assertThat(result).isNotBlank();
    }
}
