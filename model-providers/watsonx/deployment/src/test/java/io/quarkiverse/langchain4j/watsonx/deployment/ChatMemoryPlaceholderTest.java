package io.quarkiverse.langchain4j.watsonx.deployment;

import java.util.Date;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.watsonx.bean.Parameters;
import io.quarkiverse.langchain4j.watsonx.bean.TextGenerationRequest;
import io.quarkiverse.langchain4j.watsonx.runtime.config.ChatModelConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.config.LangChain4jWatsonxConfig;
import io.quarkus.test.QuarkusUnitTest;

public class ChatMemoryPlaceholderTest extends WireMockAbstract {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.base-url", WireMockUtil.URL_WATSONX_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.iam.base-url", WireMockUtil.URL_IAM_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", WireMockUtil.API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.project-id", WireMockUtil.PROJECT_ID)
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(WireMockUtil.class));

    @Override
    void handlerBeforeEach() {
        mockServers.mockIAMBuilder(200)
                .response("my_super_token", new Date())
                .build();
    }

    @ApplicationScoped
    @RegisterAiService
    public interface AiService {
        @SystemMessage("""
                You are a helpful assistant
                Context:
                {chat_memory.extractDialogue}""")
        public String answer(@MemoryId String id, @UserMessage String question);
    }

    @ApplicationScoped
    @RegisterAiService
    public interface AiService2 {
        @SystemMessage("""
                You are a helpful assistant
                Context:
                {chat_memory.extractDialogue(", ")}""")
        public String answer(@MemoryId String id, @UserMessage String question);
    }

    @ApplicationScoped
    @RegisterAiService
    public interface AiService3 {
        @SystemMessage("""
                You are a helpful assistant
                Context:
                {chat_memory.extractDialogue("U: ", "A: ", ",")}""")
        public String answer(@MemoryId String id, @UserMessage String question);
    }

    @ApplicationScoped
    @RegisterAiService(chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
    public interface NoMemoryAiService {
        @SystemMessage("""
                Context:
                {chatMemory.extractDialogue}""")
        public String rephrase(List<ChatMessage> chatMemory, @UserMessage String question);
    }

    @Inject
    AiService aiService;

    @Inject
    AiService2 aiService2;

    @Inject
    AiService3 aiService3;

    @Inject
    NoMemoryAiService noMemoryAiService;

    @Inject
    ChatMemoryStore chatMemoryStore;

    @Test
    void extract_dialogue_test() throws Exception {

        LangChain4jWatsonxConfig.WatsonConfig watsonConfig = langchain4jWatsonConfig.defaultConfig();
        ChatModelConfig chatModelConfig = watsonConfig.chatModel();
        String modelId = langchain4jWatsonFixedRuntimeConfig.defaultConfig().chatModel().modelId();
        String chatMemoryId = "userId";
        String projectId = watsonConfig.projectId();
        Parameters parameters = Parameters.builder()
                .decodingMethod(chatModelConfig.decodingMethod())
                .temperature(chatModelConfig.temperature())
                .minNewTokens(chatModelConfig.minNewTokens())
                .maxNewTokens(chatModelConfig.maxNewTokens())
                .build();

        var input = """
                You are a helpful assistant
                Context:

                Hello""";
        var body = new TextGenerationRequest(modelId, projectId, input, parameters);
        mockServers.mockWatsonxBuilder(WireMockUtil.URL_WATSONX_CHAT_API, 200)
                .body(mapper.writeValueAsString(body))
                .response("""
                        {
                            "results": [
                                {
                                    "generated_text": "Hi!",
                                    "generated_token_count": 5,
                                    "input_token_count": 50,
                                    "stop_reason": "eos_token"
                                }
                            ]
                        }""")
                .build();

        aiService.answer(chatMemoryId, "Hello");

        input = """
                You are a helpful assistant
                Context:
                User: Hello
                Assistant: Hi!
                Hello
                Hi!
                What is your name?""";
        body = new TextGenerationRequest(modelId, projectId, input, parameters);
        mockServers.mockWatsonxBuilder(WireMockUtil.URL_WATSONX_CHAT_API, 200)
                .body(mapper.writeValueAsString(body))
                .response("""
                        {
                            "results": [
                                {
                                    "generated_text": "My name is AiBot",
                                    "generated_token_count": 5,
                                    "input_token_count": 50,
                                    "stop_reason": "eos_token"
                                }
                            ]
                        }""")
                .build();

        aiService.answer(chatMemoryId, "What is your name?");
    }

    @Test
    void extract_dialogue_with_delimiter_test() throws Exception {

        LangChain4jWatsonxConfig.WatsonConfig watsonConfig = langchain4jWatsonConfig.defaultConfig();
        ChatModelConfig chatModelConfig = watsonConfig.chatModel();
        String modelId = langchain4jWatsonFixedRuntimeConfig.defaultConfig().chatModel().modelId();
        String chatMemoryId = "userId_with_delimiter";
        String projectId = watsonConfig.projectId();
        Parameters parameters = Parameters.builder()
                .decodingMethod(chatModelConfig.decodingMethod())
                .temperature(chatModelConfig.temperature())
                .minNewTokens(chatModelConfig.minNewTokens())
                .maxNewTokens(chatModelConfig.maxNewTokens())
                .build();

        var input = """
                You are a helpful assistant
                Context:

                Hello""";
        var body = new TextGenerationRequest(modelId, projectId, input, parameters);
        mockServers.mockWatsonxBuilder(WireMockUtil.URL_WATSONX_CHAT_API, 200)
                .body(mapper.writeValueAsString(body))
                .response("""
                        {
                            "results": [
                                {
                                    "generated_text": "Hi!",
                                    "generated_token_count": 5,
                                    "input_token_count": 50,
                                    "stop_reason": "eos_token"
                                }
                            ]
                        }""")
                .build();

        aiService2.answer(chatMemoryId, "Hello");

        input = """
                You are a helpful assistant
                Context:
                User: Hello, Assistant: Hi!
                Hello
                Hi!
                What is your name?""";
        body = new TextGenerationRequest(modelId, projectId, input, parameters);
        mockServers.mockWatsonxBuilder(WireMockUtil.URL_WATSONX_CHAT_API, 200)
                .body(mapper.writeValueAsString(body))
                .response("""
                        {
                            "results": [
                                {
                                    "generated_text": "My name is AiBot",
                                    "generated_token_count": 5,
                                    "input_token_count": 50,
                                    "stop_reason": "eos_token"
                                }
                            ]
                        }""")
                .build();

        aiService2.answer(chatMemoryId, "What is your name?");
    }

    @Test
    void extract_dialogue_with_all_params_test() throws Exception {

        LangChain4jWatsonxConfig.WatsonConfig watsonConfig = langchain4jWatsonConfig.defaultConfig();
        ChatModelConfig chatModelConfig = watsonConfig.chatModel();
        String modelId = langchain4jWatsonFixedRuntimeConfig.defaultConfig().chatModel().modelId();
        String chatMemoryId = "userId_with_all_params";
        String projectId = watsonConfig.projectId();
        Parameters parameters = Parameters.builder()
                .decodingMethod(chatModelConfig.decodingMethod())
                .temperature(chatModelConfig.temperature())
                .minNewTokens(chatModelConfig.minNewTokens())
                .maxNewTokens(chatModelConfig.maxNewTokens())
                .build();

        var input = """
                You are a helpful assistant
                Context:

                Hello""";
        var body = new TextGenerationRequest(modelId, projectId, input, parameters);
        mockServers.mockWatsonxBuilder(WireMockUtil.URL_WATSONX_CHAT_API, 200)
                .body(mapper.writeValueAsString(body))
                .response("""
                        {
                            "results": [
                                {
                                    "generated_text": "Hi!",
                                    "generated_token_count": 5,
                                    "input_token_count": 50,
                                    "stop_reason": "eos_token"
                                }
                            ]
                        }""")
                .build();

        aiService3.answer(chatMemoryId, "Hello");

        input = """
                You are a helpful assistant
                Context:
                U: Hello,A: Hi!
                Hello
                Hi!
                What is your name?""";
        body = new TextGenerationRequest(modelId, projectId, input, parameters);
        mockServers.mockWatsonxBuilder(WireMockUtil.URL_WATSONX_CHAT_API, 200)
                .body(mapper.writeValueAsString(body))
                .response("""
                        {
                            "results": [
                                {
                                    "generated_text": "My name is AiBot",
                                    "generated_token_count": 5,
                                    "input_token_count": 50,
                                    "stop_reason": "eos_token"
                                }
                            ]
                        }""")
                .build();

        aiService3.answer(chatMemoryId, "What is your name?");
    }

    @Test
    void extract_dialogue_no_memory_test() throws Exception {

        LangChain4jWatsonxConfig.WatsonConfig watsonConfig = langchain4jWatsonConfig.defaultConfig();
        ChatModelConfig chatModelConfig = watsonConfig.chatModel();
        String modelId = langchain4jWatsonFixedRuntimeConfig.defaultConfig().chatModel().modelId();
        String chatMemoryId = "userId_with_all_params";
        String projectId = watsonConfig.projectId();
        Parameters parameters = Parameters.builder()
                .decodingMethod(chatModelConfig.decodingMethod())
                .temperature(chatModelConfig.temperature())
                .minNewTokens(chatModelConfig.minNewTokens())
                .maxNewTokens(chatModelConfig.maxNewTokens())
                .build();

        var input = """
                Context:
                User: Hello
                Assistant: Hi!
                User: What is your name?
                Assistant: My name is AiBot
                Hello""";
        var body = new TextGenerationRequest(modelId, projectId, input, parameters);
        mockServers.mockWatsonxBuilder(WireMockUtil.URL_WATSONX_CHAT_API, 200)
                .body(mapper.writeValueAsString(body))
                .response("""
                        {
                            "results": [
                                {
                                    "generated_text": "Done!",
                                    "generated_token_count": 5,
                                    "input_token_count": 50,
                                    "stop_reason": "eos_token"
                                }
                            ]
                        }""")
                .build();

        noMemoryAiService.rephrase(chatMemoryStore.getMessages(chatMemoryId), "Hello");
    }
}
