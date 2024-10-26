package io.quarkiverse.langchain4j.watsonx.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Date;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.watsonx.bean.TextGenerationParameters;
import io.quarkiverse.langchain4j.watsonx.bean.TextGenerationRequest;
import io.quarkiverse.langchain4j.watsonx.runtime.config.GenerationModelConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.config.LangChain4jWatsonxConfig;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Multi;

public class AiGenerationServiceTest extends WireMockAbstract {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.base-url", WireMockUtil.URL_WATSONX_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.iam.base-url", WireMockUtil.URL_IAM_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", WireMockUtil.API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.project-id", WireMockUtil.PROJECT_ID)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.generation-model.model-id", "mistralai/mistral-large")
            .overrideConfigKey("quarkus.langchain4j.watsonx.mode", "generation")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(WireMockUtil.class));

    @Override
    void handlerBeforeEach() {
        mockServers.mockIAMBuilder(200)
                .grantType(langchain4jWatsonConfig.defaultConfig().iam().grantType())
                .response(WireMockUtil.BEARER_TOKEN, new Date())
                .build();
    }

    @Singleton
    @RegisterAiService(chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
    @SystemMessage("This is a systemMessage")
    interface AIService {
        @UserMessage("This is a userMessage {text}")
        String chat(String text);

        @UserMessage("This is a userMessage {text}")
        Multi<String> streaming(String text);
    }

    @Inject
    AIService aiService;

    @Inject
    ChatMemoryStore memory;

    @Test
    void chat() throws Exception {

        mockServers.mockWatsonxBuilder(WireMockUtil.URL_WATSONX_GENERATION_API, 200)
                .body(mapper.writeValueAsString(generateRequest()))
                .response(WireMockUtil.RESPONSE_WATSONX_GENERATION_API)
                .build();

        assertEquals("AI Response", aiService.chat("Hello"));
    }

    private TextGenerationRequest generateRequest() {
        LangChain4jWatsonxConfig.WatsonConfig watsonConfig = langchain4jWatsonConfig.defaultConfig();
        GenerationModelConfig chatModelConfig = watsonConfig.generationModel();
        String modelId = chatModelConfig.modelId();
        String projectId = watsonConfig.projectId();
        String input = new StringBuilder()
                .append("This is a systemMessage")
                .append(chatModelConfig.promptJoiner())
                .append("This is a userMessage Hello")
                .toString();
        TextGenerationParameters parameters = TextGenerationParameters.builder()
                .decodingMethod(chatModelConfig.decodingMethod())
                .temperature(chatModelConfig.temperature())
                .minNewTokens(chatModelConfig.minNewTokens())
                .maxNewTokens(chatModelConfig.maxNewTokens())
                .timeLimit(WireMockUtil.DEFAULT_TIME_LIMIT)
                .build();

        return new TextGenerationRequest(modelId, projectId, input, parameters);
    }
}
