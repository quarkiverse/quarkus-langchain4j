package io.quarkiverse.langchain4j.it.language;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;

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

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class QuarkusOpenAiClientLanguageResourceTest {

    @TestHTTPEndpoint(QuarkusOpenAiClientLanguageResource.class)
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
                .body(containsString("This is indeed a test"));
    }

    @Test
    public void async() {
        given()
                .baseUri(url.toString())
                .when()
                .get("async")
                .then()
                .statusCode(200)
                .body(containsString("This is indeed a test"));
    }

    @Test
    @Disabled("The Mock API does not handle streaming properly")
    public void sse() throws Exception {
        Client client = ClientBuilder.newBuilder().build();
        WebTarget target = client.target(url.toString() + "/streaming");
        // do not reconnect
        try (SseEventSource eventSource = SseEventSource.target(target)
                .reconnectingEvery(Integer.MAX_VALUE, TimeUnit.SECONDS)
                .build()) {
            CompletableFuture<List<String>> res = new CompletableFuture<>();
            List<String> collect = Collections.synchronizedList(new ArrayList<>());
            eventSource.register(
                    inboundSseEvent -> collect.add(inboundSseEvent.readData()),
                    res::completeExceptionally,
                    () -> res.complete(collect));
            eventSource.open();
            assertThat(res.get(30, TimeUnit.SECONDS)).isNotEmpty();
        }
    }

}
