package io.quarkiverse.langchain4j.huggingface.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * The Hugging Face text-generation API has no notion of tools or JSON mode. Like upstream's
 * {@code HuggingFaceChatModel}, the request must be refused up front instead of silently sending
 * a plain prompt and returning free text where the caller expected a tool call or JSON.
 */
public class HuggingFaceUnsupportedFeaturesTest {

    @RegisterExtension
    static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideConfigKey("quarkus.langchain4j.chat-model.provider", "huggingface");

    @Inject
    ChatModel chatModel;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void reset() {
        mock.reset();
        mock.chat().reply("must never be reached");
    }

    @Test
    void toolsAreRejectedBeforeAnyRequestIsSent() {
        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("What is the weather in Brno?"))
                .toolSpecifications(ToolSpecification.builder().name("getWeather").description("Weather lookup").build())
                .build();

        assertThatThrownBy(() -> chatModel.chat(request))
                .isInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("tools are not supported");
        assertThat(mock.chat().requests()).isEmpty();
    }

    @Test
    void jsonResponseFormatIsRejectedBeforeAnyRequestIsSent() {
        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("Give me a person as JSON"))
                .responseFormat(ResponseFormat.JSON)
                .build();

        assertThatThrownBy(() -> chatModel.chat(request))
                .isInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("JSON response format");
        assertThat(mock.chat().requests()).isEmpty();
    }
}
