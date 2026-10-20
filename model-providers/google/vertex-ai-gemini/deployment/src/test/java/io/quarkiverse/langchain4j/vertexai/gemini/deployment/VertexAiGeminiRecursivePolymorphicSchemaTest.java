package io.quarkiverse.langchain4j.vertexai.gemini.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import io.quarkiverse.langchain4j.auth.ModelAuthProvider;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkiverse.langchain4j.runtime.QuarkusServiceOutputParser;
import io.quarkus.test.QuarkusExtensionTest;
import tools.jackson.databind.JsonNode;

public class VertexAiGeminiRecursivePolymorphicSchemaTest {

    private static final String CHAT_MODEL_ID = "gemini-3-flash-preview";

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.vertexai.gemini.chat-model.model-id", CHAT_MODEL_ID);

    @Inject
    ChatModel chatModel;

    @Inject
    MockModelServer mock;

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
        mock.reset();
        mock.chat().reply(ChatReply.text("{}").usage(1, 1));
    }

    @Test
    void recursiveTypeIsSentAsJsonSchemaWithReferences() {
        chatModel.chat(request(TreeNode.class));

        JsonNode generationConfig = sentGenerationConfig();
        assertJsonSchemaSent(generationConfig);
        JsonNode jsonSchema = generationConfig.get("responseJsonSchema");
        assertThat(jsonSchema.has("$defs")).isTrue();
        assertThat(jsonSchema.toString()).contains("\"$ref\":\"#/$defs/");
    }

    @Test
    void polymorphicTypeIsSentAsJsonSchemaWithAnyOf() {
        chatModel.chat(request(Drawing.class));

        JsonNode generationConfig = sentGenerationConfig();
        assertJsonSchemaSent(generationConfig);
        JsonNode shape = generationConfig.get("responseJsonSchema").get("properties").get("shape");
        assertThat(shape.get("anyOf").size()).isEqualTo(2);
    }

    @Test
    void plainTypeIsStillSentAsResponseSchema() {
        chatModel.chat(request(Person.class));

        JsonNode generationConfig = sentGenerationConfig();
        assertThat(generationConfig.get("responseMimeType").asString()).isEqualTo("application/json");
        assertThat(generationConfig.get("responseSchema")).isNotNull();
        assertThat(generationConfig.get("responseJsonSchema")).isNull();
    }

    private static void assertJsonSchemaSent(JsonNode generationConfig) {
        assertThat(generationConfig.get("responseMimeType").asString()).isEqualTo("application/json");
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

    private JsonNode sentGenerationConfig() {
        return mock.chat().singleRequest().bodyAsJson().get("generationConfig");
    }

    @Singleton
    public static class DummyAuthProvider implements ModelAuthProvider {

        @Override
        public String getAuthorization(Input input) {
            return "Bearer somekey";
        }
    }
}
