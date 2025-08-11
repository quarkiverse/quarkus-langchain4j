package org.acme.example.openai.chat;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
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
import org.junit.jupiter.api.Test;

import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class QuarkusOpenAiServerClientChatResourceTest {

    @TestHTTPEndpoint(QuarkusOpenAiClientChatResource.class)
    @TestHTTPResource
    URL url;

    @Test
    public void sync() {
        given()
                .baseUri(url.toString())
                .when()
                .get("sync")
                .then()
                .statusCode(200)
                .body(TestUtils.containsStringOrMock("dynamic", "type"));
    }

    @Test
    public void async() {
        given()
                .baseUri(url.toString())
                .when()
                .get("async")
                .then()
                .statusCode(200)
                .body(TestUtils.containsStringOrMock("Scrum"));
    }

    @Test
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
            assertThat(wholeAnswer, TestUtils.containsStringOrMock("React"));
        }
    }
}
