package org.acme.example.openai.aiservices;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;

import java.net.URL;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class AssistantResourceWithFallbackTest {

    @TestHTTPEndpoint(AssistantResourceWithFallback.class)
    @TestHTTPResource
    URL url;

    @Test
    public void fallback() {
        given()
                .baseUri(url.toString())
                .get()
                .then()
                .statusCode(200)
                .body(equalTo("This is a fallback message"));
    }
}
