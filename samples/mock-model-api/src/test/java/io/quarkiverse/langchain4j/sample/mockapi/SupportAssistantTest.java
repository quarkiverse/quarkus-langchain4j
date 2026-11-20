package io.quarkiverse.langchain4j.sample.mockapi;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.ChatRequestView;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Drives the application through its REST endpoints while the mock plays the model: the test scripts what the model
 * answers and asserts on what the application sent. The mock is enabled for the test profile in
 * application.properties, and it is reset before each test method.
 */
@QuarkusTest
class SupportAssistantTest {

    @Inject
    MockModelServer mock;

    @Test
    void theSystemAndUserMessagesReachTheModel() {
        mock.chat().reply("Hello! How can I help?");

        given().body("Hi there").post("/support/chat")
                .then().statusCode(200).body(is("Hello! How can I help?"));

        ChatRequestView request = mock.chat().singleRequest().asChat();
        assertThat(request.modelName()).isEqualTo("gpt-6-luna");
        assertThat(request.temperature()).isEqualTo(0.0);
        assertThat(messageTexts(request, "system")).singleElement().asString().startsWith("You are the support assistant");
        assertThat(messageTexts(request, "user")).containsExactly("Hi there");
        assertThat(request.tools()).hasSize(1);
        ChatRequestView.ToolDefinition tool = request.tools().get(0);
        assertThat(tool.name()).isEqualTo("trackOrder");
        assertThat(tool.description()).isEqualTo("Returns the status of an order");
        assertThat(tool.parametersSchema().path("properties").propertyNames()).containsExactly("orderId");
    }

    @Test
    void aScriptedToolCallRunsTheToolAndItsResultGoesBackToTheModel() {
        // first reply: the model asks for the tool; second reply: the model answers with the tool result in hand
        mock.chat().replies(List.of(
                ChatReply.toolCall("trackOrder", Map.of("orderId", "ORD-1001")),
                ChatReply.text("Your order ORD-1001 was shipped on 2026-11-15.")));

        given().body("Where is my order ORD-1001?").post("/support/chat")
                .then().statusCode(200).body(is("Your order ORD-1001 was shipped on 2026-11-15."));

        List<CapturedRequest> requests = mock.chat().requests();
        assertThat(requests).hasSize(2);
        // the second request carries the result the application computed in OrderTools
        List<String> toolResults = messageTexts(requests.get(1).asChat(), "tool");
        assertThat(toolResults).containsExactly("ORD-1001: shipped on 2026-11-15, tracking number 1Z999");
    }

    @Test
    void structuredOutputIsParsedAndTheSchemaIsSent() {
        mock.chat().reply("{\"category\":\"billing\",\"urgent\":true,\"summary\":\"Charged twice\"}");

        given().body("I was charged twice for order ORD-1002!").post("/support/triage")
                .then().statusCode(200)
                .body("category", is("billing"))
                .body("urgent", is(true))
                .body("summary", is("Charged twice"));

        ChatRequestView request = mock.chat().singleRequest().asChat();
        assertThat(request.responseFormatType()).isEqualTo("json_schema");
        assertThat(request.responseFormatSchema().path("properties").propertyNames())
                .containsExactlyInAnyOrder("category", "urgent", "summary");
    }

    @Test
    void aFailingModelSurfacesAsAnError() {
        // the error comes back in OpenAI's error format, and the Responses API client makes a single attempt
        mock.chat().fail(429, "rate limited");

        given().body("Hi").post("/support/chat").then().statusCode(500);

        assertThat(mock.chat().singleRequest().asChat().messages()).isNotEmpty();
    }

    private static List<String> messageTexts(ChatRequestView request, String role) {
        List<String> texts = new ArrayList<>();
        for (ChatRequestView.Message message : request.messages()) {
            if (role.equals(message.role())) {
                texts.add(message.text());
            }
        }
        return texts;
    }
}
