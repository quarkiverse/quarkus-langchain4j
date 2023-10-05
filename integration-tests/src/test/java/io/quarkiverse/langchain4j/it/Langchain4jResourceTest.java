package io.quarkiverse.langchain4j.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class Langchain4jResourceTest {

    @Test
    public void testHelloEndpoint() {
        given()
                .when().get("/langchain4j")
                .then()
                .statusCode(200)
                .body(is("Hello langchain4j"));
    }
}
