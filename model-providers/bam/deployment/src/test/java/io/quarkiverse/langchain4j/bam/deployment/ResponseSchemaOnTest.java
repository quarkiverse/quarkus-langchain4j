package io.quarkiverse.langchain4j.bam.deployment;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.bam.BamRestApi;
import io.quarkiverse.langchain4j.bam.Message;
import io.quarkiverse.langchain4j.bam.Parameters;
import io.quarkiverse.langchain4j.bam.TextGenerationRequest;
import io.quarkiverse.langchain4j.bam.runtime.config.LangChain4jBamConfig;
import io.quarkus.test.QuarkusUnitTest;

public class ResponseSchemaOnTest {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideRuntimeConfigKey("quarkus.langchain4j.bam.base-url", WireMockUtil.URL)
            .overrideRuntimeConfigKey("quarkus.langchain4j.bam.api-key", WireMockUtil.API_KEY)
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(WireMockUtil.class));

    static WireMockServer wireMockServer;
    static ObjectMapper mapper;
    static WireMockUtil mockServers;
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

    //@StructuredPrompt("{response_schema} Create a poem about {topic}")
    static class PoemPrompt {

        private final String topic;

        public PoemPrompt(String topic) {
            this.topic = topic;
        }

        public String getTopic() {
            return topic;
        }
    }

    @BeforeAll
    static void beforeAll() {
        wireMockServer = new WireMockServer(options().port(WireMockUtil.PORT));
        wireMockServer.start();
        mapper = BamRestApi.objectMapper(new ObjectMapper());
        mockServers = new WireMockUtil(wireMockServer);
    }

    @AfterAll
    static void afterAll() {
        wireMockServer.stop();
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

    @Inject
    LangChain4jBamConfig langchain4jBamConfig;

    @Test
    void no_bean_ai_service() throws Exception {

        List<Message> messages = List.of(
                new Message("system", "You are a poet"),
                new Message("user", "Generate a poem about dog"));

        mockServers
                .mockBuilder(WireMockUtil.URL_CHAT_API, 200)
                .body(mapper.writeValueAsString(from(messages)))
                .response(RESPONSE)
                .build();

        assertEquals("{\n\"text\": \"Beautiful dog\"\n}", noBeanAIService.poem("dog"));
    }

    @Test
    void bean_ai_service() throws Exception {

        List<Message> messages = List.of(
                new Message("system", "You are a poet"),
                new Message("user", "Generate a poem about dog".concat(SCHEMA)));

        mockServers
                .mockBuilder(WireMockUtil.URL_CHAT_API, 200)
                .body(mapper.writeValueAsString(from(messages)))
                .response(RESPONSE)
                .build();

        assertEquals(new Poem("Beautiful dog"), beanAIService.poem("dog"));
    }

    @Test
    void schema_ai_service() throws Exception {

        List<Message> messages = List.of(
                new Message("system", SCHEMA.concat(" You are a poet")),
                new Message("user", "Generate a poem about dog ".concat(SCHEMA)));

        mockServers
                .mockBuilder(WireMockUtil.URL_CHAT_API, 200)
                .body(mapper.writeValueAsString(from(messages)))
                .response(RESPONSE)
                .build();

        assertEquals(new Poem("Beautiful dog"), schemaAIService.poem("dog"));
    }

    @Test
    void schema_system_message_ai_service() throws Exception {

        List<Message> messages = List.of(
                new Message("system", SCHEMA.concat(" You are a poet")),
                new Message("user", "Generate a poem about dog"));

        mockServers
                .mockBuilder(WireMockUtil.URL_CHAT_API, 200)
                .body(mapper.writeValueAsString(from(messages)))
                .response(RESPONSE)
                .build();

        assertEquals(new Poem("Beautiful dog"), systemMessageAIService.poem("dog"));
    }

    @Test
    void on_method_ai_service() throws Exception {

        List<Message> messages = List.of(
                new Message("user", SCHEMA.concat(" Generate a poem about dog")));

        mockServers
                .mockBuilder(WireMockUtil.URL_CHAT_API, 200)
                .body(mapper.writeValueAsString(from(messages)))
                .response(RESPONSE)
                .build();

        assertEquals(new Poem("Beautiful dog"),
                onMethodAIService.poem("{response_schema} Generate a poem about {topic}", "dog"));
    }

    @Test
    @Disabled("The response schema placeholder doesn't work with the @StructuredPrompt")
    void structured_prompt_ai_service() throws Exception {

        List<Message> messages = List.of(
                new Message("user", SCHEMA.concat("Generate a poem about dog")));

        mockServers
                .mockBuilder(WireMockUtil.URL_CHAT_API, 200)
                .body(mapper.writeValueAsString(from(messages)))
                .response(RESPONSE)
                .build();

        assertEquals(new Poem("Beautiful dog"), structuredPromptAIService.poem(new PoemPrompt("dog")));
    }

    @Test
    void system_message_on_class_ai_service() throws Exception {

        List<Message> messages = List.of(
                new Message("system", SCHEMA.concat(" You are a poet")),
                new Message("user", "Generate a poem about dog"));

        mockServers
                .mockBuilder(WireMockUtil.URL_CHAT_API, 200)
                .body(mapper.writeValueAsString(from(messages)))
                .response(RESPONSE)
                .build();

        assertEquals(new Poem("Beautiful dog"), systeMessageOnClassAIService.poem("dog"));
    }

    private TextGenerationRequest from(List<Message> messages) {
        var config = langchain4jBamConfig.defaultConfig();

        var parameters = Parameters.builder()
                .decodingMethod(config.chatModel().decodingMethod())
                .temperature(config.chatModel().temperature())
                .minNewTokens(config.chatModel().minNewTokens())
                .maxNewTokens(config.chatModel().maxNewTokens())
                .build();

        return new TextGenerationRequest(config.chatModel().modelId(), messages, parameters);
    }
}
