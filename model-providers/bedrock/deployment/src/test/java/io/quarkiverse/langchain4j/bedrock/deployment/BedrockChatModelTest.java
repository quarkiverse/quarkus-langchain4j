package io.quarkiverse.langchain4j.bedrock.deployment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.bedrock.BedrockChatModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusUnitTest;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class BedrockChatModelTest extends BedrockTestBase {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(TestCredentialsProvider.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.chat-model.model-id", "amazon.titan-text-express-v1")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.chat-model.aws.region", "eu-central-1")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.chat-model.aws.endpoint-override",
                    "http://localhost:%d".formatted(WM_PORT))
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.chat-model.aws.credentials-provider",
                    "TestCredentialsProvider")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.log-requests", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.log-responses", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.log-body", "true")
            .overrideRuntimeConfigKey("quarkus.log.category.\"io.quarkiverse.langchain4j.bedrock\".level", "DEBUG");

    @Inject
    ChatLanguageModel chatModel;

    @Test
    void should_create_bedrock_model() {
        // given

        // when

        // then
        assertThat(ClientProxy.unwrap(chatModel)).isInstanceOf(BedrockChatModel.class);
    }

    @Test
    void should_answer_a_chat_message() {
        // given
        stubFor(post(anyUrl()) //
                .willReturn(aResponse() //
                        .withStatus(200) //
                        .withHeader("Content-Type", "application/json") //
                        .withBody("""
                                {
                                  "output": {
                                    "message": {
                                      "role": "assistant",
                                      "content": [
                                        {
                                          "text": "Hello, I am good. How are you today?"
                                        }
                                      ]
                                    }
                                  },
                                  "stopReason": "end_turn",
                                  "usage": {
                                    "inputTokens": 10,
                                    "outputTokens": 21,
                                    "totalTokens": 31
                                  },
                                  "metrics": {
                                    "latencyMs": 1185
                                  }
                                }
                                """)));

        // when
        var response = chatModel.chat("Hello, how are you today?");

        // then
        assertThat(response).isNotNull().isEqualTo("Hello, I am good. How are you today?");
    }
}
