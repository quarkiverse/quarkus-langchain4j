package io.quarkiverse.langchain4j.watsonx.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.watsonx.bean.Parameters;
import io.quarkiverse.langchain4j.watsonx.bean.TextGenerationRequest;
import io.quarkus.test.QuarkusUnitTest;

public class ResponseSchemaOnTest extends WireMockAbstract {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.base-url", WireMockUtil.URL_WATSONX_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.iam.base-url", WireMockUtil.URL_IAM_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", WireMockUtil.API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.project-id", WireMockUtil.PROJECT_ID)
            .overrideConfigKey("quarkus.langchain4j.response-schema", "true")
            .overrideConfigKey("quarkus.langchain4j.watsonx.chat-model.prompt-formatter", "false")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(WireMockUtil.class));

    static String SCHEMA = "\nYou must answer strictly in the following JSON format: {\n\"text\": (type: string)\n}";
    static String RESPONSE = """
            {
                "results": [
                    {
                        "generated_token_count": 20,
                        "input_token_count": 146,
                        "stop_reason": "max_tokens",
                        "seed": 40268626,
                        "generated_text": "{\\n\\\"text\\\": \\\"Beautiful dog\\\"\\n}"
                    }
                ]
            }
            """;

    public record Poem(String text) {
    }

    // @StructuredPrompt("{response_schema} Create a poem about {topic}")
    static class PoemPrompt {

        private final String topic;

        public PoemPrompt(String topic) {
            this.topic = topic;
        }

        public String getTopic() {
            return topic;
        }
    }

    @Override
    void handlerBeforeEach() {
        mockServers.mockIAMBuilder(200)
                .grantType(langchain4jWatsonConfig.defaultConfig().iam().grantType())
                .response(WireMockUtil.BEARER_TOKEN, new Date())
                .build();
    }

    @RegisterAiService(chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
    @Singleton
    interface NoBeanAIService {

        @SystemMessage("You are a poet")
        @UserMessage("Generate a poem about {topic}")
        String poem(String topic);
    }

    @RegisterAiService(chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
    @Singleton
    interface BeanAIService {

        @SystemMessage("You are a poet")
        @UserMessage("Generate a poem about {topic}")
        Poem poem(String topic);
    }

    @RegisterAiService(chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
    @Singleton
    interface SchemaAIService {

        @SystemMessage("{response_schema} You are a poet")
        @UserMessage("Generate a poem about {topic} {response_schema}")
        Poem poem(String topic);
    }

    @RegisterAiService(chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
    @Singleton
    interface SystemMessageAIService {

        @SystemMessage("{response_schema} You are a poet")
        @UserMessage("Generate a poem about {topic}")
        Poem poem(String topic);
    }

    @RegisterAiService(chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
    @Singleton
    interface OnMethodAIService {
        Poem poem(@UserMessage String message, @V("topic") String topic);
    }

    @RegisterAiService(chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
    @Singleton
    interface StructuredPromptAIService {
        Poem poem(PoemPrompt prompt);
    }

    @RegisterAiService(chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
    @Singleton
    @SystemMessage("{response_schema} You are a poet")
    interface SysteMessageOnClassAIService {
        @UserMessage("Generate a poem about {topic}")
        Poem poem(String topic);
    }

    @Inject
    NoBeanAIService noBeanAIService;

    @Inject
    BeanAIService beanAIService;

    @Inject
    SchemaAIService schemaAIService;

    @Inject
    SystemMessageAIService systemMessageAIService;

    @Inject
    OnMethodAIService onMethodAIService;

    @Inject
    StructuredPromptAIService structuredPromptAIService;

    @Inject
    SysteMessageOnClassAIService systeMessageOnClassAIService;

    @Test
    void no_bean_ai_service() throws Exception {

        List<ChatMessage> messages = List.of(
                dev.langchain4j.data.message.SystemMessage.from("You are a poet"),
                dev.langchain4j.data.message.UserMessage.from("Generate a poem about dog"));

        mockServers.mockWatsonxBuilder(WireMockUtil.URL_WATSONX_CHAT_API, 200)
                .body(mapper.writeValueAsString(from(messages)))
                .response(RESPONSE)
                .build();

        assertEquals("{\n\"text\": \"Beautiful dog\"\n}", noBeanAIService.poem("dog"));
    }

    @Test
    void bean_ai_service() throws Exception {

        List<ChatMessage> messages = List.of(
                dev.langchain4j.data.message.SystemMessage.from("You are a poet"),
                dev.langchain4j.data.message.UserMessage.from("Generate a poem about dog".concat(SCHEMA)));

        mockServers.mockWatsonxBuilder(WireMockUtil.URL_WATSONX_CHAT_API, 200)
                .body(mapper.writeValueAsString(from(messages)))
                .response(RESPONSE)
                .build();

        assertEquals(new Poem("Beautiful dog"), beanAIService.poem("dog"));
    }

    @Test
    void schema_ai_service() throws Exception {

        List<ChatMessage> messages = List.of(
                dev.langchain4j.data.message.SystemMessage.from(SCHEMA.concat(" You are a poet")),
                dev.langchain4j.data.message.UserMessage.from("user", "Generate a poem about dog ".concat(SCHEMA)));

        mockServers.mockWatsonxBuilder(WireMockUtil.URL_WATSONX_CHAT_API, 200)
                .body(mapper.writeValueAsString(from(messages)))
                .response(RESPONSE)
                .build();

        assertEquals(new Poem("Beautiful dog"), schemaAIService.poem("dog"));
    }

    @Test
    void schema_system_message_ai_service() throws Exception {

        List<ChatMessage> messages = List.of(
                dev.langchain4j.data.message.SystemMessage.from(SCHEMA.concat(" You are a poet")),
                dev.langchain4j.data.message.UserMessage.from("Generate a poem about dog"));

        mockServers.mockWatsonxBuilder(WireMockUtil.URL_WATSONX_CHAT_API, 200)
                .body(mapper.writeValueAsString(from(messages)))
                .response(RESPONSE)
                .build();

        assertEquals(new Poem("Beautiful dog"), systemMessageAIService.poem("dog"));
    }

    @Test
    void on_method_ai_service() throws Exception {

        List<ChatMessage> messages = List.of(
                dev.langchain4j.data.message.UserMessage.from(SCHEMA.concat(" Generate a poem about dog")));

        mockServers.mockWatsonxBuilder(WireMockUtil.URL_WATSONX_CHAT_API, 200)
                .body(mapper.writeValueAsString(from(messages)))
                .response(RESPONSE)
                .build();

        assertEquals(new Poem("Beautiful dog"),
                onMethodAIService.poem("{response_schema} Generate a poem about {topic}", "dog"));
    }

    @Test
    @Disabled("The response schema placeholder doesn't work with the @StructuredPrompt")
    void structured_prompt_ai_service() throws Exception {

        List<ChatMessage> messages = List.of(
                dev.langchain4j.data.message.UserMessage.from(SCHEMA.concat("Generate a poem about dog")));

        mockServers.mockWatsonxBuilder(WireMockUtil.URL_WATSONX_CHAT_API, 200)
                .body(mapper.writeValueAsString(from(messages)))
                .response(RESPONSE)
                .build();

        assertEquals(new Poem("Beautiful dog"), structuredPromptAIService.poem(new PoemPrompt("dog")));
    }

    @Test
    void system_message_on_class_ai_service() throws Exception {

        List<ChatMessage> messages = List.of(
                dev.langchain4j.data.message.SystemMessage.from(SCHEMA.concat(" You are a poet")),
                dev.langchain4j.data.message.UserMessage.from("Generate a poem about dog"));

        mockServers.mockWatsonxBuilder(WireMockUtil.URL_WATSONX_CHAT_API, 200)
                .body(mapper.writeValueAsString(from(messages)))
                .response(RESPONSE)
                .build();

        assertEquals(new Poem("Beautiful dog"), systeMessageOnClassAIService.poem("dog"));
    }

    private TextGenerationRequest from(List<ChatMessage> messages) {
        var modelId = langchain4jWatsonFixedRuntimeConfig.defaultConfig().chatModel().modelId();
        var config = langchain4jWatsonConfig.defaultConfig();

        var parameters = Parameters.builder()
                .decodingMethod(config.chatModel().decodingMethod())
                .temperature(config.chatModel().temperature())
                .minNewTokens(config.chatModel().minNewTokens())
                .maxNewTokens(config.chatModel().maxNewTokens())
                .build();

        var input = messages.stream()
                .map(ChatMessage::text)
                .collect(Collectors.joining("\n"));

        return new TextGenerationRequest(modelId, config.projectId(), input, parameters);
    }
}
