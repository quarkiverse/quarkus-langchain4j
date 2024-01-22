package io.quarkiverse.langchain4j.bam.deployment;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Map;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;

import dev.langchain4j.model.chat.ChatLanguageModel;
import io.quarkiverse.langchain4j.bam.BamException;
import io.quarkiverse.langchain4j.bam.BamException.Code;
import io.quarkiverse.langchain4j.bam.BamException.Reason;
import io.quarkiverse.langchain4j.bam.BamRestApi;
import io.quarkiverse.langchain4j.bam.runtime.config.Langchain4jBamConfig;
import io.quarkus.test.QuarkusUnitTest;

public class HttpErrorTest {

    static WireMockServer wireMockServer;
    static ObjectMapper mapper;
    static WireMockUtil mockServers;

    @Inject
    Langchain4jBamConfig config;

    @Inject
    ChatLanguageModel model;

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideRuntimeConfigKey("quarkus.langchain4j.bam.base-url", WireMockUtil.URL)
            .overrideRuntimeConfigKey("quarkus.langchain4j.bam.api-key", WireMockUtil.API_KEY)
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(WireMockUtil.class));

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

    @Test
    void error_401() {
        mockServers.mockBuilder(401)
                .response("""
                        {
                            "status_code": 401,
                            "error": "Unauthorized",
                            "message": "API key not found",
                            "extensions": {
                                "code": "AUTH_ERROR",
                                "reason": "INVALID_AUTHORIZATION"
                            }
                        }
                        """)
                .build();

        var ex = assertThrowsExactly(BamException.class, () -> model.generate("Give me an error!"));
        if (!ex.getExtensions().isPresent())
            fail("extensions field is not present");

        assertEquals(401, ex.getStatusCode());
        assertEquals("Unauthorized", ex.getError());
        assertEquals("API key not found", ex.getMessage());
        assertEquals(Code.AUTH_ERROR, ex.getExtensions().get().getCode());
        assertEquals(Reason.INVALID_AUTHORIZATION, ex.getExtensions().get().getReason());
    }

    @Test
    void error_400() {
        mockServers.mockBuilder(400)
                .response("""
                        {
                            "status_code": 400,
                            "error": "Bad Request",
                            "message": "Parameters -> top_p must be <= 1",
                            "extensions": {
                              "code": "INVALID_INPUT",
                              "state": [{
                                "instancePath": "/parameters/top_p",
                                "params": {
                                  "comparison": "<=",
                                  "limit": 1
                                }
                              }]
                            }
                        }
                        """)
                .build();

        var ex = assertThrowsExactly(BamException.class, () -> model.generate("Give me an error!"));

        assertEquals(400, ex.getStatusCode());
        assertEquals("Bad Request", ex.getError());
        assertEquals("Parameters -> top_p must be <= 1", ex.getMessage());
        assertTrue(ex.getExtensions().isPresent());
        assertEquals(Code.INVALID_INPUT, ex.getExtensions().get().getCode());
        assertEquals(null, ex.getExtensions().get().getReason());
        assertTrue(ex.getExtensions().get().getState().isPresent());
        assertEquals(1, ex.getExtensions().get().getState().get().size());
        assertEquals("/parameters/top_p", ex.getExtensions().get().getState().get().get(0).get("instancePath"));

        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) ex.getExtensions().get().getState().get().get(0).get("params");
        assertEquals("<=", params.get("comparison"));
        assertEquals(1, params.get("limit"));
    }

    @Test
    void error_429() {
        mockServers.mockBuilder(429)
                .response("""
                        {
                            "status_code": 429,
                            "error": "Too Many Requests",
                            "message": "Exceeded 5 requests per second",
                            "extensions": {
                                "code": "TOO_MANY_REQUESTS",
                                "state": {
                                    "expires_in_ms": 2774
                                }
                            }
                        }
                        """)
                .build();

        var ex = assertThrowsExactly(BamException.class, () -> model.generate("Give me an error!"));

        assertEquals(429, ex.getStatusCode());
        assertEquals("Too Many Requests", ex.getError());
        assertEquals("Exceeded 5 requests per second", ex.getMessage());
        assertTrue(ex.getExtensions().isPresent());
        assertEquals(Code.TOO_MANY_REQUESTS, ex.getExtensions().get().getCode());
        assertEquals(null, ex.getExtensions().get().getReason());
        assertTrue(ex.getExtensions().get().getState().isPresent());
        assertEquals(1, ex.getExtensions().get().getState().get().size());
        assertEquals(2774, ex.getExtensions().get().getState().get().get(0).get("expires_in_ms"));
    }

    @Test
    void error_503() {
        mockServers.mockBuilder(503)
                .response("""
                        {
                            "status_code": 503,
                            "error": "Service Unavailable",
                            "message": "The model is temporarily unavailable.",
                            "extensions": {
                                "code": "SERVICE_UNAVAILABLE",
                                "state": {
                                    "model_id": "meta-llama/llama-2-70b-chat"
                                }
                            }
                        }
                        """)
                .build();

        var ex = assertThrowsExactly(BamException.class, () -> model.generate("Give me an error!"));

        assertEquals(503, ex.getStatusCode());
        assertEquals("Service Unavailable", ex.getError());
        assertEquals("The model is temporarily unavailable.", ex.getMessage());
        assertTrue(ex.getExtensions().isPresent());
        assertEquals(Code.SERVICE_UNAVAILABLE, ex.getExtensions().get().getCode());
        assertEquals(null, ex.getExtensions().get().getReason());
        assertTrue(ex.getExtensions().get().getState().isPresent());
        assertEquals(1, ex.getExtensions().get().getState().get().size());
        assertEquals("meta-llama/llama-2-70b-chat", ex.getExtensions().get().getState().get().get(0).get("model_id"));
    }

    @Test
    void unchecked_text_plain_error() {
        mockServers.mockBuilder(500)
                .responseMediaType(MediaType.TEXT_PLAIN)
                .response("How do you handle me?")
                .build();

        var ex = assertThrowsExactly(BamException.class, () -> model.generate("Give me an error!"));

        assertEquals(500, ex.getStatusCode());
        assertEquals("How do you handle me?", ex.getMessage());
        assertNull(ex.getError());
        assertNull(ex.getExtensions());
    }

    @Test
    void unchecked_text_plain_no_text_error() {
        mockServers.mockBuilder(500)
                .responseMediaType(MediaType.TEXT_PLAIN)
                .build();

        var ex = assertThrowsExactly(BamException.class, () -> model.generate("Give me an error!"));

        assertEquals(500, ex.getStatusCode());
        assertEquals("", ex.getMessage());
        assertNull(ex.getError());
        assertNull(ex.getExtensions());
    }

    @Test
    void unchecked_application_json_no_body_error() {
        mockServers.mockBuilder(500)
                .build();

        var ex = assertThrowsExactly(BamException.class, () -> model.generate("Give me an error!"));

        assertEquals(500, ex.getStatusCode());
        assertEquals("Unchecked error, see log for details", ex.getMessage());
        assertNull(ex.getError());
        assertNull(ex.getExtensions());
    }
}
