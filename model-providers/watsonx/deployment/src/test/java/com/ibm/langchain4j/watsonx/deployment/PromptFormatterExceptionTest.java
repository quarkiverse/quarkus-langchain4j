package com.ibm.langchain4j.watsonx.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.Tool;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.QuarkusUnitTest;

public class PromptFormatterExceptionTest {

    @RegisterAiService(tools = Calculator.class)
    interface AIService {

    }

    @Singleton
    static class Calculator {

        @Tool("calculates the sum between two numbers")
        double squareRoot(int firstNumber, int secondNumber) {
            return firstNumber + secondNumber;
        }
    }

    @Nested
    class ToolsModelNotSupported {
        @RegisterExtension
        static QuarkusUnitTest unitTest = new QuarkusUnitTest()
                .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.base-url", WireMockUtil.URL_WATSONX_SERVER)
                .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.iam.base-url", WireMockUtil.URL_IAM_SERVER)
                .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", WireMockUtil.API_KEY)
                .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.project-id", WireMockUtil.PROJECT_ID)
                .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.embedding-model.model-id",
                        WireMockUtil.DEFAULT_CHAT_MODEL)
                .overrideConfigKey("quarkus.langchain4j.watsonx.chat-model.prompt-formatter", "true")
                .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(AIService.class, Calculator.class))
                .assertException(t -> {
                    assertThat(t).isInstanceOf(RuntimeException.class)
                            .hasMessage("The tool functionality is not supported for the model \"%s\""
                                    .formatted(WireMockUtil.DEFAULT_CHAT_MODEL));
                });

        @Test
        void test() {
            fail("Should not be called");
        }
    }

    @Nested
    class ToolsPromptFormatterOff {
        @RegisterExtension
        static QuarkusUnitTest unitTest = new QuarkusUnitTest()
                .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.base-url", WireMockUtil.URL_WATSONX_SERVER)
                .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.iam.base-url", WireMockUtil.URL_IAM_SERVER)
                .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", WireMockUtil.API_KEY)
                .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.project-id", WireMockUtil.PROJECT_ID)
                .overrideConfigKey("quarkus.langchain4j.watsonx.chat-model.prompt-formatter", "false")
                .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(AIService.class, Calculator.class))
                .assertException(t -> {
                    assertThat(t).isInstanceOf(RuntimeException.class)
                            .hasMessage("The prompt-formatter must be enabled to use the tool functionality");
                });

        @Test
        void test() {
            fail("Should not be called");
        }
    }
}
