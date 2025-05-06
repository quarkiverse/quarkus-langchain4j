package io.quarkiverse.langchain4j.bedrock.deployment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static io.quarkiverse.langchain4j.bedrock.deployment.BedrockStreamHelper.createCompletion;
import static io.quarkiverse.langchain4j.bedrock.deployment.BedrockStreamHelper.decode;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.bedrock.BedrockAnthropicStreamingChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusUnitTest;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class BedrockAntrophicStreamingChatModelTest extends BedrockTestBase {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(TestCredentialsProvider.class)
                    .addClass(BedrockStreamHelper.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.chat-model.model-id", "anthropic.claude-v2")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.chat-model.aws.region", "eu-central-1")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.chat-model.aws.endpoint-override",
                    "http://localhost:%d".formatted(WM_PORT))
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.chat-model.aws.credentials-provider",
                    "TestCredentialsProvider")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.log-requests", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.log-responses", "true");

    @Inject
    StreamingChatModel streamingChatModel;

    @Test
    void should_create_bedrock_model() {
        // given

        // when

        // then
        assertThat(ClientProxy.unwrap(streamingChatModel)).isInstanceOf(BedrockAnthropicStreamingChatModel.class);
    }

    @Test
    void should_answer_a_chat_message() throws Throwable {
        // given
        var helper = BedrockStreamHelper.create();
        var expected = List.of(
                createCompletion("Hello, how are you today?"));
        stubFor(post(anyUrl())
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/vnd.amazon.eventstream") //
                        .withHeader("Transfer-Encoding", "chunked") //
                        .withHeader("Connection", "keep-alive")
                        .withBody(decode(expected))));

        // when
        streamingChatModel.chat("Hello, how are you today?", helper);
        var response = helper.awaitResponse();

        // then
        assertThat(response).isNotNull();
        assertThat(response.aiMessage()).isNotNull();
        assertThat(response.aiMessage().text()).isEqualTo("Hello, how are you today?");
    }
}
