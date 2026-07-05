package io.quarkiverse.langchain4j.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Supplier;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.NoTemplating;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.QuarkusUnitTest;

/**
 * When a {@code @UserMessage} parameter is also annotated with {@link NoTemplating}, its value is sent as-is,
 * without Qute templating, so literal {@code {...}} sequences are preserved.
 */
public class UserMessageNoTemplatingTest {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(
                            Assistant.class,
                            MyChatModelSupplier.class,
                            MyChatModel.class));

    @Inject
    Assistant assistant;

    @Test
    @ActivateRequestContext
    public void userMessageParameterIsKeptLiteral() {
        String response = assistant.summarize("Connect to {quarkus.http.host} please", 100);
        assertThat(response).isEqualTo("Connect to {quarkus.http.host} please");
    }

    @RegisterAiService(chatLanguageModelSupplier = MyChatModelSupplier.class, chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
    interface Assistant {
        @SystemMessage("Summarize in up to {maximum} tokens.")
        String summarize(@UserMessage @NoTemplating String text, int maximum);
    }

    @Singleton
    public static class MyChatModelSupplier implements Supplier<ChatModel> {

        private MyChatModel myChatModel;

        @PostConstruct
        public void init() {
            myChatModel = new MyChatModel();
        }

        @Override
        public ChatModel get() {
            return myChatModel;
        }
    }

    public static class MyChatModel implements ChatModel {
        @Override
        public ChatResponse chat(List<ChatMessage> messages) {
            throw new UnsupportedOperationException("Should not be called");
        }

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            String userText = chatRequest.messages().stream()
                    .filter(dev.langchain4j.data.message.UserMessage.class::isInstance)
                    .map(dev.langchain4j.data.message.UserMessage.class::cast)
                    .findFirst()
                    .map(dev.langchain4j.data.message.UserMessage::singleText)
                    .orElse("no user message");
            return ChatResponse.builder().aiMessage(new AiMessage(userText)).build();
        }
    }
}
