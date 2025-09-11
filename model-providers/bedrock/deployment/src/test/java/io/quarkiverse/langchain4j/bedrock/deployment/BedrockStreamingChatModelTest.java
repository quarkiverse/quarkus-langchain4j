package io.quarkiverse.langchain4j.bedrock.deployment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static io.quarkiverse.langchain4j.bedrock.deployment.BedrockStreamHelper.createContentBlockDelta;
import static io.quarkiverse.langchain4j.bedrock.deployment.BedrockStreamHelper.createContentBlockStop;
import static io.quarkiverse.langchain4j.bedrock.deployment.BedrockStreamHelper.createMessageStart;
import static io.quarkiverse.langchain4j.bedrock.deployment.BedrockStreamHelper.createMessageStop;
import static io.quarkiverse.langchain4j.bedrock.deployment.BedrockStreamHelper.createMetadata;
import static io.quarkiverse.langchain4j.bedrock.deployment.BedrockStreamHelper.decode;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.bedrock.BedrockStreamingChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusUnitTest;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class BedrockStreamingChatModelTest extends BedrockTestBase {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(TestCredentialsProvider.class)
                    .addClass(BedrockStreamHelper.class)
                    .addClass(CountingChatModelListener.class))
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
    StreamingChatModel streamingChatModel;

    @Inject
    CountingChatModelListener listener;

    @BeforeEach
    void reset_listener_counters() {
        listener.reset();
    }

    @Test
    void should_create_bedrock_model() {
        // given

        // when

        // then
        assertThat(ClientProxy.unwrap(streamingChatModel)).isInstanceOf(BedrockStreamingChatModel.class);
    }

    @Test
    void should_answer_a_chat_message() throws Throwable {
        // given
        var helper = BedrockStreamHelper.create();
        var expected = List.of(
                createMessageStart(ConversationRole.ASSISTANT),
                createContentBlockDelta(0, "Hello, how are you today?"),
                createContentBlockStop(0),
                createMessageStop(StopReason.END_TURN),
                createMetadata(100, 10, 17));
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
        assertThat(response.metadata().finishReason()).isEqualTo(FinishReason.STOP);
        assertThat(response.metadata().modelName()).isEqualTo("amazon.titan-text-express-v1");
        assertThat(response.metadata().tokenUsage()).isEqualTo(new TokenUsage(10, 17));
        assertThat(listener.onRequestCount()).isEqualTo(1);
        assertThat(listener.onResponseCount()).isEqualTo(1);
        assertThat(listener.onErrorCount()).isEqualTo(0);
    }
}
