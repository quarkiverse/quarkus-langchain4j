package io.quarkiverse.langchain4j.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpMethod;

class CurlRequestLoggerTest {

    @Test
    void shouldGenerateCurlWithMethodAndUrl() {
        HttpClientRequest request = mockRequest(HttpMethod.GET, "https://api.example.com/v1/chat",
                MultiMap.caseInsensitiveMultiMap());

        String curl = CurlRequestLogger.toCurl(request, null);

        assertTrue(curl.startsWith("curl"));
        assertTrue(curl.contains("-X GET"));
        assertTrue(curl.contains("'https://api.example.com/v1/chat'"));
        assertFalse(curl.contains("-d"));
    }

    @Test
    void shouldIncludeHeaders() {
        MultiMap headers = MultiMap.caseInsensitiveMultiMap();
        headers.add("Content-Type", "application/json");
        headers.add("Authorization", "Bearer sk-test-key-1234");

        HttpClientRequest request = mockRequest(HttpMethod.POST, "https://api.openai.com/v1/chat/completions", headers);

        String curl = CurlRequestLogger.toCurl(request, null);

        assertTrue(curl.contains("-H 'Content-Type: application/json'"));
        assertTrue(curl.contains("-H 'Authorization: Bearer sk-test-key-1234'"));
    }

    @Test
    void shouldIncludeBody() {
        MultiMap headers = MultiMap.caseInsensitiveMultiMap();
        headers.add("Content-Type", "application/json");

        HttpClientRequest request = mockRequest(HttpMethod.POST, "https://api.openai.com/v1/chat/completions", headers);
        Buffer body = Buffer.buffer("{\"model\":\"gpt-4\",\"messages\":[{\"role\":\"user\",\"content\":\"Hello\"}]}");

        String curl = CurlRequestLogger.toCurl(request, body);

        assertTrue(curl.contains("-d '{\"model\":\"gpt-4\",\"messages\":[{\"role\":\"user\",\"content\":\"Hello\"}]}'"));
    }

    @Test
    void shouldNotIncludeBodyWhenEmpty() {
        HttpClientRequest request = mockRequest(HttpMethod.POST, "https://api.example.com/v1/chat",
                MultiMap.caseInsensitiveMultiMap());
        Buffer body = Buffer.buffer();

        String curl = CurlRequestLogger.toCurl(request, body);

        assertFalse(curl.contains("-d"));
    }

    @Test
    void shouldEscapeSingleQuotesInBody() {
        MultiMap headers = MultiMap.caseInsensitiveMultiMap();
        HttpClientRequest request = mockRequest(HttpMethod.POST, "https://api.example.com/v1/chat", headers);
        Buffer body = Buffer.buffer("{\"content\":\"it's a test\"}");

        String curl = CurlRequestLogger.toCurl(request, body);

        assertTrue(curl.contains("-d '{\"content\":\"it'\\''s a test\"}'"));
    }

    @Test
    void shouldEscapeSingleQuotesInHeaders() {
        MultiMap headers = MultiMap.caseInsensitiveMultiMap();
        headers.add("X-Custom", "value'with'quotes");

        HttpClientRequest request = mockRequest(HttpMethod.POST, "https://api.example.com/v1/chat", headers);

        String curl = CurlRequestLogger.toCurl(request, null);

        assertTrue(curl.contains("-H 'X-Custom: value'\\''with'\\''quotes'"));
    }

    @Test
    void shouldProduceSingleLineOutput() {
        MultiMap headers = MultiMap.caseInsensitiveMultiMap();
        headers.add("Content-Type", "application/json");

        HttpClientRequest request = mockRequest(HttpMethod.POST, "https://api.example.com/v1/chat", headers);
        Buffer body = Buffer.buffer("{\"test\":true}");

        String curl = CurlRequestLogger.toCurl(request, body);

        assertFalse(curl.contains("\n"));
    }

    @Test
    void escapeForSingleQuoteShouldHandleNull() {
        assertEquals("", CurlRequestLogger.escapeForSingleQuote(null));
    }

    @Test
    void escapeForSingleQuoteShouldHandleEmptyString() {
        assertEquals("", CurlRequestLogger.escapeForSingleQuote(""));
    }

    @Test
    void escapeForSingleQuoteShouldHandleNoQuotes() {
        assertEquals("hello world", CurlRequestLogger.escapeForSingleQuote("hello world"));
    }

    private static HttpClientRequest mockRequest(HttpMethod method, String absoluteURI, MultiMap headers) {
        HttpClientRequest request = Mockito.mock(HttpClientRequest.class);
        Mockito.when(request.getMethod()).thenReturn(method);
        Mockito.when(request.absoluteURI()).thenReturn(absoluteURI);
        Mockito.when(request.headers()).thenReturn(headers);
        return request;
    }
}
