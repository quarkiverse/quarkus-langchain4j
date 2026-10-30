package io.quarkiverse.langchain4j.vertexai.runtime.gemini;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class VertxAiGeminiRestApiTest {

    @Test
    void defaultBaseUrlMatchesTheEndpointOfTheLocation() {
        assertEquals("https://aiplatform.googleapis.com", VertxAiGeminiRestApi.defaultBaseUrl("global"));
        assertEquals("https://europe-west4-aiplatform.googleapis.com", VertxAiGeminiRestApi.defaultBaseUrl("europe-west4"));
        assertEquals("https://aiplatform.us.rep.googleapis.com", VertxAiGeminiRestApi.defaultBaseUrl("us"));
        assertEquals("https://aiplatform.eu.rep.googleapis.com", VertxAiGeminiRestApi.defaultBaseUrl("eu"));
    }
}
