package io.quarkiverse.langchain4j.tests.moderation;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

@QuarkusTest
public class GuardrailTest {

    @Test
    public void censoredAnswer() {
        Response response = given().body("Hello, my name is Meatbag")
                .post("/chatbot/moderated");
        Assertions.assertEquals(200, response.statusCode());
        Assertions.assertFalse(response.body()
                        .asString().toLowerCase()
                        .contains("meatbag"),
                "Guardrail allowed prohibited word in the body: " + response.body().asString());
        Assertions.assertEquals("[The AI answered with expletive]",
                response.body().asString());
    }

    @Test
    public void unCensoredAnswer() {
        Response response = given().body("Hello, my name is Fleabag")
                .post("/chatbot/moderated");
        Assertions.assertEquals(200, response.statusCode());
        Assertions.assertTrue(response.body()
                .asString().toLowerCase()
                .contains("fleabag"));
    }

}
