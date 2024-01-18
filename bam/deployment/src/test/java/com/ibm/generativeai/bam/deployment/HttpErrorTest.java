package com.ibm.generativeai.bam.deployment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
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

import io.quarkiverse.langchain4j.bam.BamChatModel;
import io.quarkiverse.langchain4j.bam.BamException;
import io.quarkiverse.langchain4j.bam.BamException.Code;
import io.quarkiverse.langchain4j.bam.BamException.Reason;
import io.quarkiverse.langchain4j.bam.BamRestApi;
import io.quarkiverse.langchain4j.bam.runtime.BamRecorder;
import io.quarkiverse.langchain4j.bam.runtime.config.Langchain4jBamConfig;
import io.quarkus.test.QuarkusUnitTest;

public class HttpErrorTest {

    static WireMockServer wireMockServer;
    static ObjectMapper mapper;

    @Inject
    Langchain4jBamConfig config;

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideRuntimeConfigKey("quarkus.langchain4j.bam.base-url", Util.URL)
            .overrideRuntimeConfigKey("quarkus.langchain4j.bam.api-key", Util.API_KEY)
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(Util.class));

    @BeforeAll
    static void beforeAll() {
        wireMockServer = new WireMockServer(options().port(Util.PORT));
        wireMockServer.start();
        mapper = BamRestApi.objectMapper(new ObjectMapper());
    }

    @AfterAll
    static void afterAll() {
        wireMockServer.stop();
    }

    @Test
    void error_401() {
        wireMockServer.stubFor(
                post(urlEqualTo(Util.URL_CHAT_API.formatted(config.version())))
                        .withHeader("Authorization", equalTo("Bearer %s".formatted(Util.API_KEY)))
                        .willReturn(
                                aResponse()
                                        .withStatus(401)
                                        .withHeader("Content-Type", MediaType.APPLICATION_JSON)
                                        .withBody("""
                                                {
                                                    "status_code": 401,
                                                    "error": "Unauthorized",
                                                    "message": "API key not found",
                                                    "extensions": {
                                                        "code": "AUTH_ERROR",
                                                        "reason": "INVALID_AUTHORIZATION"
                                                    }
                                                }
                                                """)));

        BamRecorder recorder = new BamRecorder();
        BamChatModel model = (BamChatModel) recorder.chatModel(config).get();
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
        wireMockServer.stubFor(
                post(urlEqualTo(Util.URL_CHAT_API.formatted(config.version())))
                        .withHeader("Authorization", equalTo("Bearer %s".formatted(Util.API_KEY)))
                        .willReturn(
                                aResponse()
                                        .withStatus(400)
                                        .withHeader("Content-Type", MediaType.APPLICATION_JSON)
                                        .withBody("""
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
                                                """)));

        BamRecorder recorder = new BamRecorder();
        BamChatModel model = (BamChatModel) recorder.chatModel(config).get();
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
        wireMockServer.stubFor(
                post(urlEqualTo(Util.URL_CHAT_API.formatted(config.version())))
                        .withHeader("Authorization", equalTo("Bearer %s".formatted(Util.API_KEY)))
                        .willReturn(
                                aResponse()
                                        .withStatus(429)
                                        .withHeader("Content-Type", MediaType.APPLICATION_JSON)
                                        .withBody("""
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
                                                """)));

        BamRecorder recorder = new BamRecorder();
        BamChatModel model = (BamChatModel) recorder.chatModel(config).get();
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
        wireMockServer.stubFor(
                post(urlEqualTo(Util.URL_CHAT_API.formatted(config.version())))
                        .withHeader("Authorization", equalTo("Bearer %s".formatted(Util.API_KEY)))
                        .willReturn(
                                aResponse()
                                        .withStatus(503)
                                        .withHeader("Content-Type", MediaType.APPLICATION_JSON)
                                        .withBody("""
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
                                                """)));

        BamRecorder recorder = new BamRecorder();
        BamChatModel model = (BamChatModel) recorder.chatModel(config).get();
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
        wireMockServer.stubFor(
                post(urlEqualTo(Util.URL_CHAT_API.formatted(config.version())))
                        .withHeader("Authorization", equalTo("Bearer %s".formatted(Util.API_KEY)))
                        .willReturn(
                                aResponse()
                                        .withStatus(500)
                                        .withHeader("Content-Type", MediaType.TEXT_PLAIN)
                                        .withBody("How do you handle me?")));

        BamRecorder recorder = new BamRecorder();
        BamChatModel model = (BamChatModel) recorder.chatModel(config).get();
        var ex = assertThrowsExactly(BamException.class, () -> model.generate("Give me an error!"));

        assertEquals(500, ex.getStatusCode());
        assertEquals("How do you handle me?", ex.getMessage());
        assertNull(ex.getError());
        assertNull(ex.getExtensions());
    }

    @Test
    void unchecked_text_plain_no_text_error() {
        wireMockServer.stubFor(
                post(urlEqualTo(Util.URL_CHAT_API.formatted(config.version())))
                        .withHeader("Authorization", equalTo("Bearer %s".formatted(Util.API_KEY)))
                        .willReturn(
                                aResponse()
                                        .withStatus(500)
                                        .withHeader("Content-Type", MediaType.TEXT_PLAIN)));

        BamRecorder recorder = new BamRecorder();
        BamChatModel model = (BamChatModel) recorder.chatModel(config).get();
        var ex = assertThrowsExactly(BamException.class, () -> model.generate("Give me an error!"));

        assertEquals(500, ex.getStatusCode());
        assertEquals("", ex.getMessage());
        assertNull(ex.getError());
        assertNull(ex.getExtensions());
    }

    @Test
    void unchecked_application_json_no_body_error() {
        wireMockServer.stubFor(
                post(urlEqualTo(Util.URL_CHAT_API.formatted(config.version())))
                        .withHeader("Authorization", equalTo("Bearer %s".formatted(Util.API_KEY)))
                        .willReturn(
                                aResponse()
                                        .withStatus(500)
                                        .withHeader("Content-Type", MediaType.APPLICATION_JSON)
                                        .withBody("")));

        BamRecorder recorder = new BamRecorder();
        BamChatModel model = (BamChatModel) recorder.chatModel(config).get();
        var ex = assertThrowsExactly(BamException.class, () -> model.generate("Give me an error!"));

        assertEquals(500, ex.getStatusCode());
        assertEquals("Unchecked error, see log for details", ex.getMessage());
        assertNull(ex.getError());
        assertNull(ex.getExtensions());
    }
}
