package org.acme.example.openai.aiservices;

import static io.restassured.RestAssured.given;

import java.net.URL;

import org.acme.example.openai.TestUtils;
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
                .queryParam("message", "Hello, my name is Quarkus")
                .get()
                .then()
                .statusCode(200)
                .body(TestUtils.containsStringOrMock("Quarkus"));
    }
}
