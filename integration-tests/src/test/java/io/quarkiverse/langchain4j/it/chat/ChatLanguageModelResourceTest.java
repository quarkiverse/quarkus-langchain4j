package io.quarkiverse.langchain4j.it.chat;

import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.containsString;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class ChatLanguageModelResourceTest {

    @Test
    public void blocking() {
        when().get("/chat/blocking")
                .then()
                .statusCode(200)
                .body(containsString("MockGPT"));
    }
}
