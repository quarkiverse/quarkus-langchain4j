package org.acme.example.openai.aiservices;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.fail;

import java.net.URL;
import java.util.Collection;

import jakarta.inject.Inject;

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
    public void noTimedAnnotations() throws InterruptedException {
        given()
                .baseUri(url.toString())
                .get("a1")
                .then()
                .statusCode(200)
                .body(containsString("MockGPT"));

        waitForMeters(registry.find("langchain4j.aiservices").tag("aiservice", "AssistantResourceWithMetrics$Assistant1")
                .tag("method", "chat").timers(), 1);
    }

    @Test
    public void timedAnnotationOnClass() throws InterruptedException {
        given()
                .baseUri(url.toString())
                .get("a2")
                .then()
                .statusCode(200)
                .body(containsString("MockGPT"));

        waitForMeters(registry.find("langchain4j.aiservices").tag("aiservice", "AssistantResourceWithMetrics$Assistant2")
                .tag("method", "chat").tag("key", "value").timers(), 1);
    }

    @Test
    public void timedAnnotationOnMethod() throws InterruptedException {
        given()
                .baseUri(url.toString())
                .get("a2c2")
                .then()
                .statusCode(200)
                .body(containsString("MockGPT"));

        waitForMeters(registry.find("a2c2").timers(), 1);
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
