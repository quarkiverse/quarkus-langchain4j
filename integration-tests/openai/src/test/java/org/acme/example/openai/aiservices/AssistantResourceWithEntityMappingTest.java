package org.acme.example.openai.aiservices;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import java.net.URL;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class AssistantResourceWithEntityMappingTest {

    @TestHTTPEndpoint(EntityMappedResource.class)
    @TestHTTPResource
    URL url;

    @Test
    public void getMany() {
        given()
                .baseUri(url.toString() + "/generateMapped")
                .queryParam("message", "This is a test")
                .post()
                .then()
                .statusCode(200)
                .body("$", hasSize(1)) // Ensure that the response is an array with exactly one item
                .body("[0].foo", equalTo("asd")) // Check that foo is set correctly
                .body("[0].bar", equalTo(1)) // Check that bar is 100
                .body("[0].baz", equalTo(2.0F));
    }
}
