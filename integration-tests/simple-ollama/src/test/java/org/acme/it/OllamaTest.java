package org.acme.it;

import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

@QuarkusTest
class OllamaTest {

    @Test
    void devservice() {
        RestAssured.get("/earth/flat")
                .then()
                .statusCode(200)
                .body(CoreMatchers.containsString("Earth"));
    }

}
