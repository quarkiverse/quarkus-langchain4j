package org.acme.example.openai.aiservices;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import java.net.URL;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class AssistantResourceTest {

    @TestHTTPEndpoint(AssistantResource.class)
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
                .body(containsString("MockGPT"));
    }
}
