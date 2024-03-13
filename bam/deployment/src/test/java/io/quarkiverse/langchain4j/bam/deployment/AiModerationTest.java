package io.quarkiverse.langchain4j.bam.deployment;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;

import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.moderation.Moderation;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.model.output.Response;
import io.quarkiverse.langchain4j.bam.BamRestApi;
import io.quarkiverse.langchain4j.bam.ModerationRequest;
import io.quarkiverse.langchain4j.bam.ModerationRequest.Threshold;
import io.quarkiverse.langchain4j.bam.runtime.BamRecorder;
import io.quarkiverse.langchain4j.bam.runtime.config.LangChain4jBamConfig;
import io.quarkiverse.langchain4j.runtime.NamedModelUtil;
import io.quarkus.test.QuarkusUnitTest;

public class AiModerationTest {

    static WireMockServer wireMockServer;
    static ObjectMapper mapper;
    static WireMockUtil mockServers;

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideRuntimeConfigKey("quarkus.langchain4j.bam.base-url", WireMockUtil.URL)
            .overrideRuntimeConfigKey("quarkus.langchain4j.bam.api-key", WireMockUtil.API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.bam.moderation-model.implicit-hate", "0.8")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bam.moderation-model.hap", "0.7")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bam.moderation-model.stigma", "0.6")

            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class).addClass(WireMockUtil.class).addClass(BamRecordUtil.class));

    @BeforeAll
    static void beforeAll() {
        wireMockServer = new WireMockServer(options().port(WireMockUtil.PORT));
        wireMockServer.start();
        mapper = BamRestApi.objectMapper(new ObjectMapper());
        mockServers = new WireMockUtil(wireMockServer);
    }

    @AfterAll
    static void afterAll() {
        wireMockServer.stop();
    }

    @Inject
    ModerationModel moderationModel;

    @Inject
    LangChain4jBamConfig langchain4jBamConfig;

    @Test
    void moderation_implicit_hate() throws Exception {
        var input = "I want to kill you!";
        ModerationRequest body = new ModerationRequest(input, new Threshold(0.8f), new Threshold(0.7f), new Threshold(0.6f));

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
                                    ],
                                    "hap": [
                                        {
                                            "score": 0.0001,
                                            "flagged": false,
                                            "success": true
                                        }
                                    ],
                                    "stigma": [
                                        {
                                            "score": 0.0001,
                                            "flagged": false,
                                            "success": true
                                        }
                                    ]
                                }
                            ]
                        }
                        """)
                .build();

        Response<Moderation> response = moderationModel.moderate(input);
        assertNotNull(response);
        assertNotNull(response.content());
        assertTrue(response.content().flagged());
        assertEquals(input, response.content().flaggedText());
    }

    @Test
    void moderation_hap() throws Exception {
        var input = "I want to kill you!";
        ModerationRequest body = new ModerationRequest(input, new Threshold(0.8f), new Threshold(0.7f), new Threshold(0.6f));

        mockServers
                .mockBuilder(WireMockUtil.URL_MODERATION_API, 200)
                .body(mapper.writeValueAsString(body))
                .response("""
                        {
                            "results": [
                                {
                                    "implicit_hate": [
                                        {
                                            "score": 0.1,
                                            "flagged": false,
                                            "success": true
                                        }
                                    ],
                                    "hap": [
                                        {
                                            "score": 0.8,
                                            "flagged": true,
                                            "success": true,
                                            "position": {
                                                "start": 0,
                                                "end": 18
                                            }
                                        }
                                    ],
                                    "stigma": [
                                        {
                                            "score": 0.0001,
                                            "flagged": false,
                                            "success": true
                                        }
                                    ]
                                }
                            ]
                        }
                        """)
                .build();

        Response<Moderation> response = moderationModel.moderate(input);
        assertNotNull(response);
        assertNotNull(response.content());
        assertTrue(response.content().flagged());
        assertEquals(input, response.content().flaggedText());
    }

    @Test
    void moderation_stigma() throws Exception {
        var input = "I want to kill you!";
        ModerationRequest body = new ModerationRequest(input, new Threshold(0.8f), new Threshold(0.7f), new Threshold(0.6f));

        mockServers
                .mockBuilder(WireMockUtil.URL_MODERATION_API, 200)
                .body(mapper.writeValueAsString(body))
                .response("""
                        {
                            "results": [
                                {
                                    "implicit_hate": [
                                        {
                                            "score": 0.1,
                                            "flagged": false,
                                            "success": true
                                        }
                                    ],
                                    "hap": [
                                        {
                                            "score": 0.1,
                                            "flagged": false,
                                            "success": true
                                        }
                                    ],
                                    "stigma": [
                                        {
                                            "score": 0.7,
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

        Response<Moderation> response = moderationModel.moderate(input);
        assertNotNull(response);
        assertNotNull(response.content());
        assertTrue(response.content().flagged());
        assertEquals(input, response.content().flaggedText());
    }

    @Test
    void moderation_messages_to_check() throws Exception {
        BamRecorder recorder = new BamRecorder();
        BamRecordUtil recordUtil = new BamRecordUtil(langchain4jBamConfig);

        var input = "I want to kill you!";
        var moderationRequest = new ModerationRequest(input, new Threshold(0.1f), new Threshold(0.1f), new Threshold(0.1f));

        mockServers
                .mockBuilder(WireMockUtil.URL_MODERATION_API, 200)
                .body(mapper.writeValueAsString(moderationRequest))
                .response("""
                        {
                            "results": [
                                {
                                    "implicit_hate": [
                                        {
                                            "score": 0.9,
                                            "flagged": true,
                                            "success": true,
                                            "position": {
                                                "start": 0,
                                                "end": 18
                                            }
                                        }
                                    ],
                                    "hap": [
                                        {
                                            "score": 0.9,
                                            "flagged": true,
                                            "success": true,
                                            "position": {
                                                "start": 0,
                                                "end": 18
                                            }
                                        }
                                    ],
                                    "stigma": [
                                        {
                                            "score": 0.9,
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

        var overrideConfig = recordUtil.override(List.of(ChatMessageType.SYSTEM), 0.1f, 0.1f, 0.1f);
        var m = recorder.moderationModel(overrideConfig, NamedModelUtil.DEFAULT_NAME).get();
        var response = m.moderate(List.of(UserMessage.from(input)));

        //
        // In this case, the mock server returns more flagged items, but the message to moderate is only one and of type SYSTEM.
        //

        assertFalse(response.content().flagged());
        assertNull(response.content().flaggedText());
    }

    @Test
    void moderation_recorder() throws Exception {

        // TEST: No kind of thresholds
        assertRecorderModeration("", null);

        // TEST: Only implicitHate threshold.
        assertRecorderModeration("implicit_hate", new ModerationRequest("input", new Threshold(0.3f), null, null));

        // TEST: Only hap threshold.
        assertRecorderModeration("hap", new ModerationRequest("input", null, new Threshold(0.3f), null));

        // TEST: Only stigma threshold.
        assertRecorderModeration("stigma", new ModerationRequest("input", null, null, new Threshold(0.3f)));
    }

    private void assertRecorderModeration(String moderation, ModerationRequest moderationRequest)
            throws JsonProcessingException {

        Float hap = null;
        Float stigma = null;
        Float implicitHate = null;
        BamRecorder recorder = new BamRecorder();
        BamRecordUtil recordUtil = new BamRecordUtil(langchain4jBamConfig);

        if (moderationRequest != null) {

            hap = moderationRequest.hap() == null ? null : moderationRequest.hap().threshold();
            stigma = moderationRequest.stigma() == null ? null : moderationRequest.stigma().threshold();
            implicitHate = moderationRequest.implicitHate() == null ? null : moderationRequest.implicitHate().threshold();

            mockServers
                    .mockBuilder(WireMockUtil.URL_MODERATION_API, 200)
                    .body(mapper.writeValueAsString(moderationRequest))
                    .response("""
                            {
                                "results": [
                                    {
                                        "FIELD": [
                                            {
                                                "score": 0.3,
                                                "flagged": true,
                                                "success": true,
                                                "position": {
                                                    "start": 0,
                                                    "end": 4
                                                }
                                            }
                                        ]
                                    }
                                ]
                            }
                            """.replaceFirst("FIELD", moderation))
                    .build();
        }

        var m = recorder.moderationModel(recordUtil.override(implicitHate, hap, stigma), NamedModelUtil.DEFAULT_NAME).get();
        var response = m.moderate("input");

        if (moderationRequest != null) {
            assertTrue(response.content().flagged());
            assertEquals("input", response.content().flaggedText());
        } else {
            assertFalse(response.content().flagged());
            assertNull(response.content().flaggedText());
        }
    }
}
