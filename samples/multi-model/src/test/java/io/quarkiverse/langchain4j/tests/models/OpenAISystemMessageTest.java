package io.quarkiverse.langchain4j.tests.models;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(OpenAIProfile.class)
public class OpenAISystemMessageTest {
    @Test
    public void customSystemMessage() {
        Response response = given().body("Who are you and what can you do?").post("/chat");
        assertEquals(200, response.statusCode());
        String answer = response.body().asString();
        String unified = answer.toLowerCase();
        assertTrue(unified.contains("bob"), "System message was ignored! \n" + answer);
        assertTrue(unified.contains("openai"), "Answer doesn't contain provider name! \n" + answer);
        assertTrue(unified.contains("constrained"), "System message about the model was not applied! \n" + answer);
        assertFalse(unified.contains("hallucinat"), "Temperature overload was not applied! \n" + answer);
    }
}
