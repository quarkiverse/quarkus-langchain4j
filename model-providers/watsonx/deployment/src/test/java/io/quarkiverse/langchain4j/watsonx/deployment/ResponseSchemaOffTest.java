package io.quarkiverse.langchain4j.watsonx.deployment;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Date;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.QuarkusUnitTest;

public class ResponseSchemaOffTest extends WireMockAbstract {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.base-url", WireMockUtil.URL_WATSONX_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.iam.base-url", WireMockUtil.URL_IAM_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", WireMockUtil.API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.project-id", WireMockUtil.PROJECT_ID)
            .overrideConfigKey("quarkus.langchain4j.watsonx.mode", "generation")
            .overrideConfigKey("quarkus.langchain4j.response-schema", "false")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(WireMockUtil.class));

    @Override
    void handlerBeforeEach() {
        mockServers.mockIAMBuilder(200)
                .grantType(langchain4jWatsonConfig.defaultConfig().iam().grantType())
                .response(WireMockUtil.BEARER_TOKEN, new Date())
                .build();
    }

    @RegisterAiService(chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
    @Singleton
    interface OnMethodAIService {
        String poem1(@UserMessage String message, @V("topic") String topic);

        Poem poem2(@UserMessage String message);

        @UserMessage("{message}")
        Poem poem3(String message);

        @SystemMessage("SystemMessage")
        @UserMessage("{message}")
        Poem poem4(String message);

        public record Poem(String text) {
        };
    }

    @Inject
    OnMethodAIService onMethodAIService;

    static String POEM_RESPONSE = """
            {
                "model_id": "mistralai/mistral-large",
                "created_at": "2024-01-21T17:06:14.052Z",
                "results": [
                    {
                        "generated_text": "{ \\\"text\\\": \\\"Poem\\\" }",
                        "generated_token_count": 5,
                        "input_token_count": 50,
                        "stop_reason": "eos_token",
                        "seed": 2123876088
                    }
                ]
            }
            """;

    @Test
    void test_poem_1() throws Exception {
        var ex = assertThrows(RuntimeException.class,
                () -> onMethodAIService.poem1("{response_schema} Generate a poem about {topic}", "dog"));
        assertEquals(
                "The {response_schema} placeholder cannot be used if the property quarkus.langchain4j.response-schema is set to false. Found in: io.quarkiverse.langchain4j.watsonx.deployment.ResponseSchemaOffTest$OnMethodAIService",
                ex.getMessage());

        mockServers.mockWatsonxBuilder(WireMockUtil.URL_WATSONX_GENERATION_API, 200)
                .body(matchingJsonPath("$.input", equalTo("Generate a poem about dog")))
                .response(WireMockUtil.RESPONSE_WATSONX_GENERATION_API)
                .build();

        assertEquals("AI Response", onMethodAIService.poem1("Generate a poem about {topic}", "dog"));
    }

    @Test
    void test_poem_2() {
        mockServers.mockWatsonxBuilder(WireMockUtil.URL_WATSONX_GENERATION_API, 200)
                .body(matchingJsonPath("$.input", equalTo("Generate a poem about dog")))
                .response(POEM_RESPONSE)
                .build();

        assertEquals("Poem", onMethodAIService.poem2("Generate a poem about dog").text);
    }

    @Test
    void test_poem_3() {
        mockServers.mockWatsonxBuilder(WireMockUtil.URL_WATSONX_GENERATION_API, 200)
                .body(matchingJsonPath("$.input", equalTo("Generate a poem about dog")))
                .response(POEM_RESPONSE)
                .build();

        assertEquals("Poem", onMethodAIService.poem3("Generate a poem about dog").text);
    }

    @Test
    void test_poem_4() {
        mockServers.mockWatsonxBuilder(WireMockUtil.URL_WATSONX_GENERATION_API, 200)
                .body(matchingJsonPath("$.input", equalTo("SystemMessage\nGenerate a poem about dog")))
                .response(POEM_RESPONSE)
                .build();

        assertEquals("Poem", onMethodAIService.poem4("Generate a poem about dog").text);
    }
}
