package org.acme.example.openai.chat.ollama;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@Disabled("Integration tests that need an ollama server running")
@QuarkusTest
class ToolChatLanguageModelResourceTest {

    @Test
    public void testToolEndpoint() {
        given()
                .when().get("/chat-with-tools/expenses")
                .then()
                .statusCode(200)
                .body(containsString("Expense hp12"));
    }

}
