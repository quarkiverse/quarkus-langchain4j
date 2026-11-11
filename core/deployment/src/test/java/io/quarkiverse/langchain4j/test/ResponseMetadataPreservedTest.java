package io.quarkiverse.langchain4j.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Supplier;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.QuarkusUnitTest;

/**
 * AI service methods that hand the model response back to the caller must keep the response
 * metadata the model produced.
 * <p>
 * Upstream {@code DefaultAiServices} passes the real {@link ChatResponse} to
 * {@code ServiceOutputParser#parse}, which builds {@code Response} from
 * {@code chatResponse.tokenUsage()} / {@code chatResponse.finishReason()} and returns the
 * {@link ChatResponse} itself for a {@code ChatResponse} return type.
 */
public class ResponseMetadataPreservedTest {

    private static final TokenUsage TOKEN_USAGE = new TokenUsage(11, 22);

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(MyAiService.class, MyChatModelSupplier.class, MyChatModel.class));

    @Inject
    MyAiService aiService;

    @Test
    @ActivateRequestContext
    void legacyResponseReturnTypeKeepsTokenUsageAndFinishReason() {
        Response<AiMessage> response = aiService.asResponse("hi");

        assertThat(response.content().text()).isEqualTo("hello");
        assertThat(response.tokenUsage()).isEqualTo(TOKEN_USAGE);
        assertThat(response.finishReason()).isEqualTo(FinishReason.LENGTH);
    }

    @Test
    @ActivateRequestContext
    void chatResponseReturnTypeKeepsMetadata() {
        ChatResponse response = aiService.asChatResponse("hi");

        assertThat(response.aiMessage().text()).isEqualTo("hello");
        assertThat(response.tokenUsage()).isEqualTo(TOKEN_USAGE);
        assertThat(response.finishReason()).isEqualTo(FinishReason.LENGTH);
        assertThat(response.modelName()).isEqualTo("stub-model");
    }

    @Test
    @ActivateRequestContext
    void resultOfResponseReturnTypeKeepsTokenUsageAndFinishReason() {
        Result<Response<AiMessage>> result = aiService.asResultOfResponse("hi");

        assertThat(result.content().content().text()).isEqualTo("hello");
        assertThat(result.content().tokenUsage()).isEqualTo(TOKEN_USAGE);
        assertThat(result.content().finishReason()).isEqualTo(FinishReason.LENGTH);
    }

    @RegisterAiService(chatLanguageModelSupplier = MyChatModelSupplier.class)
    public interface MyAiService {

        Response<AiMessage> asResponse(@UserMessage String message);

        ChatResponse asChatResponse(@UserMessage String message);

        Result<Response<AiMessage>> asResultOfResponse(@UserMessage String message);
    }

    public static class MyChatModelSupplier implements Supplier<ChatModel> {

        @Override
        public ChatModel get() {
            return new MyChatModel();
        }
    }

    public static class MyChatModel implements ChatModel {

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from("hello"))
                    .metadata(ChatResponseMetadata.builder()
                            .id("response-id")
                            .modelName("stub-model")
                            .tokenUsage(TOKEN_USAGE)
                            .finishReason(FinishReason.LENGTH)
                            .build())
                    .build();
        }
    }
}
