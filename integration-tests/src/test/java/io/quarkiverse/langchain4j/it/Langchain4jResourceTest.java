package io.quarkiverse.langchain4j.it;

import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.containsString;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class Langchain4jResourceTest {

    @Test
    public void chat() {
        when().get("/langchain4j/chat")
                .then()
                .statusCode(200)
                .body(containsString("MockGPT"));
    }
}
