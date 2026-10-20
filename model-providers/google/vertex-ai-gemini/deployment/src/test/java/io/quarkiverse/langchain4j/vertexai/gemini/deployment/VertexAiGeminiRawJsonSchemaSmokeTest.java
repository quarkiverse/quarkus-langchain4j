package io.quarkiverse.langchain4j.vertexai.gemini.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonRawSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.quarkiverse.langchain4j.auth.ModelAuthProvider;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

public class VertexAiGeminiRawJsonSchemaSmokeTest {

    private static final String API_KEY = "somekey";
    private static final String CHAT_MODEL_ID = "gemini-2.5-flash";

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.vertexai.gemini.log-requests", "true");

    @Inject
    ChatModel chatModel;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void reset() {
        mock.reset();
    }

    @Test
    void should_support_raw_json_schema() throws JsonProcessingException {
        String rawSchema = """
                {
                  "type": "object",
                  "properties": {
                    "name": {
                      "type": "string"
                    },
                    "birthDate": {
                      "type": "string",
                      "format": "date"
                    },
                    "height": {
                      "type": "number",
                      "minimum": 1.83,
                      "maximum": 1.88
                    },
                    "role": {
                      "type": "string",
                      "enum": ["developer", "maintainer", "researcher"]
                    },
                    "isAvailable": { "type": "boolean" },
                    "tags": {
                      "type": "array",
                      "items": {
                        "type": "string"
                      },
                      "minItems": 1,
                      "maxItems": 5
                    },
                    "address": {
                      "type": "object",
                      "properties": {
                        "city": { "type": "string" },
                        "streetName": { "type": "string" },
                        "streetNumber": { "type": "string" }
                      },
                      "required": ["city", "streetName", "streetNumber"],
                      "additionalProperties": true
                    }
                  },
                  "required": ["name", "birthDate", "height", "role", "tags", "address"]
                }
                """;

        String responseJson = """
                {
                  "name": "Sherlock Holmes",
                  "birthDate": "1990-11-28",
                  "height": 1.85,
                  "role": "researcher",
                  "isAvailable": true,
                  "tags": ["detective", "violinist"],
                  "address": {
                    "city": "London",
                    "streetName": "Baker Street",
                    "streetNumber": "221B"
                  }
                }
                """;

        JsonRawSchema jsonRawSchema = JsonRawSchema.builder().schema(rawSchema).build();
        JsonSchema jsonSchema = JsonSchema.builder().rootElement(jsonRawSchema).build();

        ResponseFormat responseFormat = ResponseFormat.builder()
                .type(ResponseFormatType.JSON)
                .jsonSchema(jsonSchema)
                .build();

        mock.chat().reply(ChatReply.text(responseJson).usage(50, 100));

        ChatResponse response = chatModel.chat(ChatRequest.builder()
                .messages(UserMessage.from("Tell me about Sherlock Holmes"))
                .responseFormat(responseFormat)
                .build());

        assertThat(response.aiMessage().text()).isEqualTo(responseJson);

        // Verify the request body contains responseJsonSchema (raw) and not responseSchema (mapped)
        CapturedRequest capturedRequest = mock.chat().singleRequest();
        assertThat(capturedRequest.path()).isEqualTo(String.format(
                "/vertexai-gemini/v1/projects/mock-project/locations/us-central1/publishers/google/models/%s:generateContent",
                CHAT_MODEL_ID));
        assertThat(capturedRequest.header("Authorization")).isEqualTo("Bearer " + API_KEY);
        String requestBody = capturedRequest.body();

        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> request = mapper.readValue(requestBody, Map.class);
        Map<String, Object> generationConfig = (Map<String, Object>) request.get("generationConfig");

        assertThat(generationConfig).containsKey("responseJsonSchema");
        assertThat(generationConfig.get("responseSchema")).isNull();
        assertThat(generationConfig.get("responseMimeType")).isEqualTo("application/json");

        // Verify the raw schema was passed through correctly
        Map<String, Object> sentSchema = (Map<String, Object>) generationConfig.get("responseJsonSchema");
        assertThat(sentSchema.get("type")).isEqualTo("object");
        Map<String, Object> properties = (Map<String, Object>) sentSchema.get("properties");
        assertThat(properties).containsKeys("name", "birthDate", "height", "role", "isAvailable", "tags", "address");
    }

    @Singleton
    public static class DummyAuthProvider implements ModelAuthProvider {

        @Override
        public String getAuthorization(Input input) {
            return "Bearer " + API_KEY;
        }

    }
}