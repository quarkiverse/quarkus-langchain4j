package io.quarkiverse.langchain4j.bam.deployment;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;

import dev.langchain4j.service.Moderate;
import dev.langchain4j.service.ModerationException;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.bam.BamRestApi;
import io.quarkiverse.langchain4j.bam.ModerationRequest;
import io.quarkiverse.langchain4j.bam.ModerationRequest.Threshold;
import io.quarkus.test.QuarkusUnitTest;

public class AiModerationMultiTest {

    static WireMockServer wireMockServer;
    static ObjectMapper mapper;
    static WireMockUtil mockServers;

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideRuntimeConfigKey("quarkus.langchain4j.bam.service1.log-requests", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bam.service1.base-url", WireMockUtil.URL)
            .overrideRuntimeConfigKey("quarkus.langchain4j.bam.service1.api-key", WireMockUtil.API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.bam.service1.moderation-model.implicit-hate", "0.5")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bam.service1.moderation-model.hap", "0.5")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bam.service1.moderation-model.stigma", "0.5")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bam.service2.log-requests", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bam.service2.base-url", WireMockUtil.URL)
            .overrideRuntimeConfigKey("quarkus.langchain4j.bam.service2.api-key", WireMockUtil.API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.bam.service2.moderation-model.implicit-hate", "0.1")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bam.service2.moderation-model.hap", "0.1")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bam.service2.moderation-model.stigma", "0.1")
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class).addClass(WireMockUtil.class).addClass(BamRecordUtil.class));

    @BeforeAll
    static void beforeAll() {
        wireMockServer = new WireMockServer(options().port(WireMockUtil.PORT));
        wireMockServer.start();
        mapper = BamRestApi.objectMapper(new ObjectMapper());
        mockServers = new WireMockUtil(wireMockServer);
    }

    @BeforeEach
    void beforeEach() {
        wireMockServer.resetRequests();
        wireMockServer.resetScenarios();
    }

    @AfterAll
    static void afterAll() {
        wireMockServer.stop();
    }

    @RegisterAiService(modelName = "service1")
    @Singleton
    interface AIService1 {

        @Moderate
        @SystemMessage("This is a systemMessage")
        @UserMessage("{text}")
        String chat(String text);
    }

    @RegisterAiService(modelName = "service2")
    @Singleton
    interface AIService2 {

        @Moderate
        @SystemMessage("This is a systemMessage")
        @UserMessage("{text}")
        String chat(String text);
    }

    @Inject
    AIService1 service1;

    @Inject
    AIService2 service2;

    @Test
    void moderation_service1() throws Exception {
        var input = "I want to kill you!";

        mockServers
                .mockBuilder(WireMockUtil.URL_CHAT_API, 200)
                .response("""
                        {
                            "results": [
                                {
                                    "generated_token_count": 20,
                                    "input_token_count": 146,
                                    "stop_reason": "max_tokens",
                                    "seed": 40268626,
                                    "generated_text": "AI Response"
                                }
                            ]
                        }
                        """)
                .build();

        var body = new ModerationRequest(input, new Threshold(0.5f), new Threshold(0.5f), new Threshold(0.5f));
        mockServers
                .mockBuilder(WireMockUtil.URL_MODERATION_API, 200)
                .body(mapper.writeValueAsString(body))
                .response("""
                        {
                            "results": [
                                {
                                    "implicit_hate": [
                                        {
                                            "score": 0.9571548104286194,
                                            "flagged": true,
                                            "success": true,
                                            "position": {
                                                "start": 0,
                                                "end": 18
                                            }
                                        }
                                    ]
                                }
                            ]
                        }
                        """)
                .build();

        assertThrowsExactly(ModerationException.class, () -> service1.chat(input));
    }

    @Test
    void moderation_service2() throws Exception {
        var input = "I want to kill you!";
        mockServers
                .mockBuilder(WireMockUtil.URL_CHAT_API, 200)
                .response("""
                        {
                            "results": [
                                {
                                    "generated_token_count": 20,
                                    "input_token_count": 146,
                                    "stop_reason": "max_tokens",
                                    "seed": 40268626,
                                    "generated_text": "AI Response"
                                }
                            ]
                        }
                        """)
                .build();

        var body = new ModerationRequest(input, new Threshold(0.1f), new Threshold(0.1f), new Threshold(0.1f));
        mockServers
                .mockBuilder(WireMockUtil.URL_MODERATION_API, 200)
                .body(mapper.writeValueAsString(body))
                .response("""
                        {
                            "results": [
                                {
                                    "implicit_hate": [
                                        {
                                            "score": 0.9571548104286194,
                                            "flagged": true,
                                            "success": true,
                                            "position": {
                                                "start": 0,
                                                "end": 18
                                            }
                                        }
                                    ]
                                }
                            ]
                        }
                        """)
                .build();

        assertThrowsExactly(ModerationException.class, () -> service2.chat(input));
    }
}
