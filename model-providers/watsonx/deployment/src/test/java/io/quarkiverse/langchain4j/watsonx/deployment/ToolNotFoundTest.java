package io.quarkiverse.langchain4j.watsonx.deployment;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Date;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import io.quarkus.test.QuarkusUnitTest;

public class ToolNotFoundTest extends WireMockAbstract {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.base-url", WireMockUtil.URL_WATSONX_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.iam.base-url", WireMockUtil.URL_IAM_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", WireMockUtil.API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.project-id", WireMockUtil.PROJECT_ID)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.tool-choice", "mytool")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(WireMockUtil.class));

    @Override
    void handlerBeforeEach() {
        mockServers.mockIAMBuilder(200)
                .grantType(langchain4jWatsonConfig.defaultConfig().iam().grantType())
                .response(WireMockUtil.BEARER_TOKEN, new Date())
                .build();
    }

    @Inject
    ChatLanguageModel model;

    @Test
    void test() {

        mockServers.mockWatsonxBuilder(WireMockUtil.URL_WATSONX_CHAT_API, 200)
                .response(WireMockUtil.RESPONSE_WATSONX_CHAT_API)
                .build();

        assertThrows(IllegalArgumentException.class,
                () -> model.chat(ChatRequest.builder().messages(UserMessage.from("test")).build()),
                "If tool-choice is 'REQUIRED', at least one tool must be specified.");

        assertThrows(IllegalArgumentException.class,
                () -> model.chat(ChatRequest
                        .builder()
                        .messages(UserMessage.from("test"))
                        .parameters(
                                ChatRequestParameters.builder()
                                        .toolSpecifications(ToolSpecification.builder().name("sum").build())
                                        .build())
                        .build()),
                "The tool with name 'mytool' is not available in the list of tools sent to the model. Tool lists: [sum]");
    }

}
