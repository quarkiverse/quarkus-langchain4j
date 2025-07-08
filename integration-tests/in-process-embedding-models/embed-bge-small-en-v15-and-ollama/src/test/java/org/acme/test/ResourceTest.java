package org.acme.test;

import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.containsString;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class ResourceTest {

    @Test
    void test() {
        when().get("/embedding")
                .then()
                .statusCode(200)
                .body(containsString("Ollama"));
    }

}
