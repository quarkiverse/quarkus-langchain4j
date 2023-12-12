package org.acme.example.azure.openai.chat;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import java.net.URL;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class ChatLanguageModelResourceTest {

    @TestHTTPEndpoint(ChatLanguageModelResource.class)
    @TestHTTPResource
    URL url;

    @Test
    public void basic() {
        given()
                .baseUri(url.toString())
                .get("basic")
                .then()
                .statusCode(200)
                .body(containsString("MockGPT"));
    }

}
