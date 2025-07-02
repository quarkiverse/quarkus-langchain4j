package io.quarkiverse.langchain4j.watsonx.deployment;

import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.API_KEY;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.DEFAULT_TIME_LIMIT;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.PROJECT_ID;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_IAM_SERVER;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_GENERATION_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_SERVER;

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
import io.quarkiverse.langchain4j.watsonx.bean.TextGenerationParameters;
import io.quarkiverse.langchain4j.watsonx.bean.TextGenerationRequest;
import io.quarkiverse.langchain4j.watsonx.runtime.config.GenerationModelConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.config.LangChain4jWatsonxConfig;
import io.quarkus.test.QuarkusUnitTest;

public class ChatMemoryPlaceholderTest extends WireMockAbstract {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.base-url", URL_WATSONX_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.iam.base-url", URL_IAM_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.project-id", PROJECT_ID)
            .overrideConfigKey("quarkus.langchain4j.watsonx.mode", "generation")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(WireMockUtil.class));

    @Override
    void handlerBeforeEach() {
        mockIAMBuilder(200)
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

        String chatMemoryId = "userId";

        var input = """
                You are a helpful assistant
                Context:

                Hello""";

        mockWatsonxBuilder(URL_WATSONX_GENERATION_API, 200)
                .body(mapper.writeValueAsString(createRequest(input)))
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

        mockWatsonxBuilder(URL_WATSONX_GENERATION_API, 200)
                .body(mapper.writeValueAsString(createRequest(input)))
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

        String chatMemoryId = "userId_with_delimiter";

        var input = """
                You are a helpful assistant
                Context:

                Hello""";

        mockWatsonxBuilder(URL_WATSONX_GENERATION_API, 200)
                .body(mapper.writeValueAsString(createRequest(input)))
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

        mockWatsonxBuilder(URL_WATSONX_GENERATION_API, 200)
                .body(mapper.writeValueAsString(createRequest(input)))
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

        String chatMemoryId = "userId_with_all_params";

        var input = """
                You are a helpful assistant
                Context:

                Hello""";

        mockWatsonxBuilder(URL_WATSONX_GENERATION_API, 200)
                .body(mapper.writeValueAsString(createRequest(input)))
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

        mockWatsonxBuilder(URL_WATSONX_GENERATION_API, 200)
                .body(mapper.writeValueAsString(createRequest(input)))
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

        String chatMemoryId = "userId_with_all_params";

        var input = """
                Context:
                User: Hello
                Assistant: Hi!
                User: What is your name?
                Assistant: My name is AiBot
                Hello""";

        mockWatsonxBuilder(URL_WATSONX_GENERATION_API, 200)
                .body(mapper.writeValueAsString(createRequest(input)))
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

    private TextGenerationRequest createRequest(String input) {
        LangChain4jWatsonxConfig.WatsonConfig watsonConfig = langchain4jWatsonConfig.defaultConfig();
        GenerationModelConfig chatModelConfig = watsonConfig.generationModel();
        String modelId = chatModelConfig.modelName();
        String spaceId = watsonConfig.spaceId().orElse(null);
        String projectId = watsonConfig.projectId().orElse(null);
        TextGenerationParameters parameters = TextGenerationParameters.builder()
                .decodingMethod(chatModelConfig.decodingMethod())
                .temperature(chatModelConfig.temperature())
                .minNewTokens(chatModelConfig.minNewTokens())
                .maxNewTokens(chatModelConfig.maxNewTokens())
                .timeLimit(DEFAULT_TIME_LIMIT)
                .stopSequences(List.of())
                .build();

        return new TextGenerationRequest(modelId, spaceId, projectId, input, parameters);
    }
}
