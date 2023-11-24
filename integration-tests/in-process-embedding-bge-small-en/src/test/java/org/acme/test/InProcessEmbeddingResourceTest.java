package org.acme.test;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

@QuarkusTest
class InProcessEmbeddingResourceTest {

    @Test
    void test() {
        var s = RestAssured.given()
                .body("This is a sentence.")
                .post("/in-process-embedding")
                .andReturn().asString();
        Assertions.assertThat(s)
                .contains("bgeSmallEnEmbeddingModel: 384\n" + "embeddingModel: 384");
    }

}
