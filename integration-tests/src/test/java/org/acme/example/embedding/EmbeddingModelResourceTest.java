package org.acme.example.embedding;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import java.net.URL;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@Disabled("mockgpt does not implement embeddings")
public class EmbeddingModelResourceTest {

    @TestHTTPEndpoint(EmbeddingModelResource.class)
    @TestHTTPResource
    URL url;

    @Test
    public void blocking() {
        given()
                .baseUri(url.toString())
                .get("blocking")
                .then()
                .statusCode(200)
                .body(containsString("MockGPT"));
    }

}
