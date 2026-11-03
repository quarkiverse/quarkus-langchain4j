package io.quarkiverse.langchain4j.vertexai.gemini.deployment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import io.quarkiverse.langchain4j.auth.ModelAuthProvider;
import io.quarkiverse.langchain4j.runtime.QuarkusServiceOutputParser;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

public class VertexAiGeminiRecursivePolymorphicSchemaTest extends WiremockAware {

    private static final String CHAT_MODEL_ID = "gemini-3-flash-preview";
    private static final String MODEL_URL = "/v1/projects/dummy/locations/dummy/publishers/google/models/" + CHAT_MODEL_ID;
    private static final String RESPONSE = """
            {"candidates":[{"content":{"role":"model","parts":[{"text":"{}"}]},"finishReason":"STOP"}],
             "usageMetadata":{"promptTokenCount":1,"candidatesTokenCount":1,"totalTokenCount":2}}""";

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.vertexai.gemini.base-url", WiremockAware.wiremockUrlForConfig())
            .overrideRuntimeConfigKey("quarkus.langchain4j.vertexai.gemini.chat-model.model-id", CHAT_MODEL_ID);

    @Inject
    ChatModel chatModel;

    public record TreeNode(String name, List<TreeNode> children) {
    }

    public sealed interface Shape permits Circle, Square {
    }

    public record Circle(double radius) implements Shape {
    }

    public record Square(double side) implements Shape {
    }

    public record Drawing(String title, Shape shape) {
    }

    public record Person(String name, int age) {
    }

    @BeforeEach
    void setUp() {
        resetRequests();
        wiremock().register(post(urlPathEqualTo(MODEL_URL + ":generateContent"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(RESPONSE)));
    }

    @Test
    void recursiveTypeIsSentAsJsonSchemaWithReferences() throws Exception {
        chatModel.chat(request(TreeNode.class));

        JsonNode generationConfig = sentGenerationConfig();
        assertJsonSchemaSent(generationConfig);
        JsonNode jsonSchema = generationConfig.get("responseJsonSchema");
        assertThat(jsonSchema.has("$defs")).isTrue();
        assertThat(jsonSchema.toString()).contains("\"$ref\":\"#/$defs/");
    }

    @Test
    void polymorphicTypeIsSentAsJsonSchemaWithAnyOf() throws Exception {
        chatModel.chat(request(Drawing.class));

        JsonNode generationConfig = sentGenerationConfig();
        assertJsonSchemaSent(generationConfig);
        JsonNode shape = generationConfig.get("responseJsonSchema").get("properties").get("shape");
        assertThat(shape.get("anyOf")).hasSize(2);
    }

    @Test
    void plainTypeIsStillSentAsResponseSchema() throws Exception {
        chatModel.chat(request(Person.class));

        JsonNode generationConfig = sentGenerationConfig();
        assertThat(generationConfig.get("responseMimeType").asText()).isEqualTo("application/json");
        assertThat(generationConfig.get("responseSchema")).isNotNull();
        assertThat(generationConfig.get("responseJsonSchema")).isNull();
    }

    private static void assertJsonSchemaSent(JsonNode generationConfig) {
        assertThat(generationConfig.get("responseMimeType").asText()).isEqualTo("application/json");
        assertThat(generationConfig.get("responseSchema")).isNull();
        assertThat(generationConfig.get("responseJsonSchema")).isNotNull();
    }

    private static ChatRequest request(Class<?> responseType) {
        JsonSchema jsonSchema = new QuarkusServiceOutputParser().jsonSchema(responseType).orElseThrow();
        return ChatRequest.builder()
                .messages(UserMessage.from("Describe it"))
                .responseFormat(ResponseFormat.builder().type(ResponseFormatType.JSON).jsonSchema(jsonSchema).build())
                .build();
    }

    private JsonNode sentGenerationConfig() throws Exception {
        return new ObjectMapper().readTree(singleLoggedRequest().getBodyAsString()).get("generationConfig");
    }

    @Singleton
    public static class DummyAuthProvider implements ModelAuthProvider {

        @Override
        public String getAuthorization(Input input) {
            return "Bearer somekey";
        }
    }
}
