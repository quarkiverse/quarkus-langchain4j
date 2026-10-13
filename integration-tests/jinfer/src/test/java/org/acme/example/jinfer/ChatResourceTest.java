package org.acme.example.jinfer;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class ChatResourceTest {

    @Test
    void chatModelIsWired() {
        given()
                .when().get("/chat/wired")
                .then()
                .statusCode(200)
                .body(is("true"));
    }
}
