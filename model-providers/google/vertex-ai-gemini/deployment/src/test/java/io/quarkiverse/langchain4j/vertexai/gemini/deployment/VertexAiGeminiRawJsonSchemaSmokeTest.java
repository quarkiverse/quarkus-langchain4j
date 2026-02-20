package io.quarkiverse.langchain4j.vertexai.gemini.deployment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonRawSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.quarkiverse.langchain4j.auth.ModelAuthProvider;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

public class VertexAiGeminiRawJsonSchemaSmokeTest extends WiremockAware {

    private static final String API_KEY = "somekey";
    private static final String CHAT_MODEL_ID = "gemini-2.5-flash";

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.vertexai.gemini.base-url", WiremockAware.wiremockUrlForConfig())
            .overrideRuntimeConfigKey("quarkus.langchain4j.vertexai.gemini.log-requests", "true");

    @Inject
    ChatModel chatModel;

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

        wiremock().register(
                post(urlEqualTo(
                        String.format(
                                "/v1/projects/dummy/locations/dummy/publishers/google/models/%s:generateContent",
                                CHAT_MODEL_ID)))
                        .withHeader("Authorization", equalTo("Bearer " + API_KEY))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody(String.format("""
                                        {
                                          "candidates": [
                                            {
                                              "content": {
                                                "role": "model",
                                                "parts": [
                                                  {
                                                    "text": %s
                                                  }
                                                ]
                                              },
                                              "finishReason": "STOP"
                                            }
                                          ],
                                          "usageMetadata": {
                                            "promptTokenCount": 50,
                                            "candidatesTokenCount": 100,
                                            "totalTokenCount": 150
                                          }
                                        }
                                        """, new ObjectMapper().writeValueAsString(responseJson)))));

        ChatResponse response = chatModel.chat(ChatRequest.builder()
                .messages(dev.langchain4j.data.message.UserMessage.from("Tell me about Sherlock Holmes"))
                .responseFormat(responseFormat)
                .build());

        assertThat(response.aiMessage().text()).isNotBlank();

        // Verify the request body contains responseJsonSchema (raw) and not responseSchema (mapped)
        LoggedRequest loggedRequest = singleLoggedRequest();
        String requestBody = new String(loggedRequest.getBody());

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