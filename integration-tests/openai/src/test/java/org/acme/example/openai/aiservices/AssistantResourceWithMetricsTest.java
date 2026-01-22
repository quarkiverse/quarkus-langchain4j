package org.acme.example.openai.aiservices;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.fail;

import java.net.URL;
import java.util.Collection;

import jakarta.inject.Inject;

import org.acme.example.openai.TestUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class AssistantResourceWithMetricsTest {

    @TestHTTPEndpoint(AssistantResourceWithMetrics.class)
    @TestHTTPResource
    URL url;

    @Inject
    MeterRegistry registry;

    @BeforeAll
    static void addSimpleRegistry() {
        Metrics.globalRegistry.add(new SimpleMeterRegistry());
    }

    @Test
    public void noMicrometerAnnotations() throws InterruptedException {
        given()
                .baseUri(url.toString())
                .get("a1")
                .then()
                .statusCode(200)
                .body(TestUtils.containsStringOrMock("test"));

        waitForMeters(
                registry.find("langchain4j.aiservices.timed")
                        .tag("aiservice", "AssistantResourceWithMetrics$Assistant1")
                        .tag("method", "chat")
                        .timers(),
                1);
        waitForMeters(
                registry.find("langchain4j.aiservices.counted")
                        .tag("aiservice", "AssistantResourceWithMetrics$Assistant1")
                        .tag("method", "chat")
                        .tag("result", "success")
                        .tag("exception", "none")
                        .counters(),
                1);
    }

    @Test
    public void micrometerAnnotationOnClass() throws InterruptedException {
        given()
                .baseUri(url.toString())
                .get("a2")
                .then()
                .statusCode(200)
                .body(TestUtils.containsStringOrMock("test"));

        waitForMeters(
                registry.find("langchain4j.aiservices.timed")
                        .tag("aiservice", "AssistantResourceWithMetrics$Assistant2")
                        .tag("method", "chat")
                        .tag("key", "value")
                        .timers(),
                1);
        waitForMeters(
                registry.find("langchain4j.aiservices.counted")
                        .tag("aiservice", "AssistantResourceWithMetrics$Assistant2")
                        .tag("method", "chat")
                        .tag("result", "success")
                        .tag("exception", "none")
                        .counters(),
                1);
    }

    @Test
    public void micrometerAnnotationOnMethod() throws InterruptedException {
        given()
                .baseUri(url.toString())
                .get("a2c2")
                .then()
                .statusCode(200)
                .body(TestUtils.containsStringOrMock("test"));

        waitForMeters(registry.find("a2c2-timed").timers(), 1);
        waitForMeters(registry.find("a2c2-counted")
                .tag("result", "success")
                .tag("exception", "none").counters(), 1);
    }

    @Test
    public void failedMethodInvocation() throws InterruptedException {
        given()
                .baseUri(url.toString())
                .get("a3")
                .then()
                .statusCode(500);

        waitForMeters(
                registry.find("langchain4j.aiservices.counted").tag("aiservice", "AssistantResourceWithMetrics$Assistant3")
                        .tag("method", "chat")
                        .tag("result", "failure")
                        .tag("exception", "TemplateException").counters(),
                1);
    }

    public <T> void waitForMeters(Collection<T> collection, int count) throws InterruptedException {
        int i = 0;
        do {
            Thread.sleep(10);
        } while (collection.size() < count && i++ < 5);

        if (i > 5) {
            fail("Unable to find the requested metrics");
        }
    }
}
