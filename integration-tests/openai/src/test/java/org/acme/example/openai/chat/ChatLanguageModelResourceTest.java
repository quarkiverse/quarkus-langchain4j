package org.acme.example.openai.chat;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.sse.SseEventSource;

import org.acme.example.openai.TestUtils;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class ChatLanguageModelResourceTest {

    @TestHTTPEndpoint(ChatLanguageModelResource.class)
    @TestHTTPResource
    URL url;

    @Test
    public void blocking() {
        given()
                .baseUri(url.toString())
                .get("blocking")
                .then()
                .statusCode(200)
                .body(TestUtils.containsStringOrMock("1969"));
    }

    @Test
    @EnabledIfSystemProperty(named = "quarkus.test.profile", matches = "cloud-llm", disabledReason = "mockgpt does not implement moderations")
    @Disabled("https://github.com/quarkiverse/quarkus-langchain4j/issues/1693")
    public void sse() throws Exception {
        Client client = ClientBuilder.newBuilder().build();
        WebTarget target = client.target(url.toString() + "/streaming");
        // do not reconnect
        try (SseEventSource eventSource = SseEventSource.target(target).reconnectingEvery(Integer.MAX_VALUE, TimeUnit.SECONDS)
                .build()) {
            CompletableFuture<List<String>> res = new CompletableFuture<>();
            List<String> collect = Collections.synchronizedList(new ArrayList<>());
            eventSource.register(
                    inboundSseEvent -> collect.add(inboundSseEvent.readData()),
                    res::completeExceptionally,
                    () -> res.complete(collect));
            eventSource.open();
            List<String> result = res.get(30, TimeUnit.SECONDS);
            assertFalse(result.isEmpty());
            String wholeAnswer = result.stream().reduce("", String::concat);
            MatcherAssert.assertThat(wholeAnswer, TestUtils.containsStringOrMock("Java"));
        }
    }

    @Test
    public void template() {
        given()
                .baseUri(url.toString())
                .get("template")
                .then()
                .statusCode(200)
                .body(TestUtils.containsStringOrMock("bake"));
    }

    @Test
    public void structuredPrompt() {
        given()
                .baseUri(url.toString())
                .get("structuredPrompt")
                .then()
                .statusCode(200)
                .body(TestUtils.containsStringOrMock("Instructions"));
    }

    @Test
    @Disabled("https://github.com/quarkiverse/quarkus-langchain4j/issues/1695")
    public void memory() {
        given()
                .baseUri(url.toString())
                .get("memory")
                .then()
                .statusCode(200)
                .body(TestUtils.containsStringOrMock("Finland"));
    }
}
