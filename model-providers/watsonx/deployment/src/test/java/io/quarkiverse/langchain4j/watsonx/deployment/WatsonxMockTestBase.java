package io.quarkiverse.langchain4j.watsonx.deployment;

import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.API_KEY;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.GRANT_TYPE;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.IAM_200_RESPONSE;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.URL_IAM_GENERATE_TOKEN;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkiverse.langchain4j.mockapi.RawResponse;
import io.quarkiverse.langchain4j.watsonx.runtime.config.LangChain4jWatsonxConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.spi.JsonProvider;

/**
 * Shared helpers of the tests that run against the mock model API. The mock serves the IAM token endpoint itself
 * ({@link #MOCK_TOKEN}) unless a test stubs it.
 */
public abstract class WatsonxMockTestBase {

    static final String MOCK_TOKEN = "mock-iam-token";

    static ObjectMapper mapper;

    @Inject
    LangChain4jWatsonxConfig langchain4jWatsonConfig;

    @Inject
    MockModelServer mock;

    @BeforeAll
    static void initMapper() {
        // the provider's mapper needs the running application
        mapper = JsonProvider.MAPPER;
    }

    @BeforeEach
    void resetMock() {
        mock.reset();
    }

    @AfterEach
    void checkIamRequests() {
        // a test that made no IAM token request has nothing to check
        iamRequests();
    }

    /**
     * The recorded requests, without the IAM token requests.
     */
    List<CapturedRequest> apiRequests() {
        return mock.requests().stream().filter(r -> !r.path().equals(URL_IAM_GENERATE_TOKEN)).toList();
    }

    /**
     * The recorded requests with the given method and path.
     */
    List<CapturedRequest> requests(String method, String path) {
        return mock.requests().stream().filter(r -> r.method().equals(method) && r.path().equals(path)).toList();
    }

    /**
     * The recorded IAM token requests, each checked for the form the provider sends.
     */
    List<CapturedRequest> iamRequests() {
        List<CapturedRequest> iam = requests("POST", URL_IAM_GENERATE_TOKEN);
        assertThat(iam).allSatisfy(r -> {
            assertThat(r.header("Content-Type")).startsWith("application/x-www-form-urlencoded");
            assertThat(formParams(r.body())).containsEntry("apikey", API_KEY).containsEntry("grant_type", GRANT_TYPE);
        });
        return iam;
    }

    /**
     * Checks what every stub of a watsonx.ai endpoint matched on: the path, a dated {@code version} query parameter
     * and the IAM token of the mock.
     */
    static void assertWatsonxRequest(CapturedRequest request, String path) {
        assertWatsonxRequest(request, path, MOCK_TOKEN);
    }

    /**
     * Checks what every stub of a watsonx.ai endpoint matched on: the path, a dated {@code version} query parameter
     * and the IAM token.
     */
    static void assertWatsonxRequest(CapturedRequest request, String path, String token) {
        assertThat(request.path()).isEqualTo(path);
        assertThat(queryParams(request)).hasEntrySatisfying("version",
                version -> assertThat(version).matches("\\d{4}-\\d{2}-\\d{2}"));
        assertThat(request.header("Authorization")).isEqualTo("Bearer " + token);
    }

    /**
     * Checks that the request body is the given JSON document.
     */
    static void assertJsonBody(CapturedRequest request, String expectedJson) {
        try {
            assertThat(mapper.readTree(request.body())).isEqualTo(mapper.readTree(expectedJson));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Checks that the request body is the JSON serialization of the given object.
     */
    static void assertJsonBody(CapturedRequest request, Object expected) {
        try {
            assertJsonBody(request, mapper.writeValueAsString(expected));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    static Map<String, String> queryParams(CapturedRequest request) {
        return formParams(request.query());
    }

    private static Map<String, String> formParams(String encoded) {
        Map<String, String> params = new LinkedHashMap<>();
        if (encoded == null || encoded.isEmpty()) {
            return params;
        }
        for (String pair : encoded.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) {
                params.put(URLDecoder.decode(pair, StandardCharsets.UTF_8), "");
                continue;
            }
            params.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
        }
        return params;
    }

    /**
     * An IAM token response with the given token and expiration.
     */
    static RawResponse iamToken(String token, Date expiration) {
        return RawResponse.json(200,
                IAM_200_RESPONSE.formatted(token, TimeUnit.MILLISECONDS.toSeconds(expiration.getTime())));
    }

    /**
     * A server-sent events response with the given body, verbatim.
     */
    static RawResponse sse(String events) {
        return RawResponse.of(200, Map.of("Content-Type", "text/event-stream"), events);
    }
}
