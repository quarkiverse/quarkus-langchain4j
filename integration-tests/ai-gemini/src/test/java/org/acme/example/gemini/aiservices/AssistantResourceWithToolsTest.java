package org.acme.example.gemini.aiservices;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import java.net.URL;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class AssistantResourceWithToolsTest {

    @TestHTTPEndpoint(AssistantWithToolsResource.class)
    @TestHTTPResource
    URL url;

    @Test
    public void get() {
        given()
                .baseUri(url.toString())
                .queryParam("message", "This is a test")
                .get()
                .then()
                .statusCode(200)
                .body(equalTo("Nice to meet you:Nice to meet you:true:true"));
    }
}
