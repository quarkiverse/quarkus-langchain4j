package io.quarkiverse.langchain4j.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.function.Supplier;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RecordChatHistory;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.QuarkusUnitTest;

public class RecordChatHistoryWithoutStoreBeanTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(ServiceWithNoStore.class, FakeChatModelSupplier.class))
            .assertException(t -> assertThat(t).isInstanceOf(jakarta.enterprise.inject.spi.DeploymentException.class)
                    .rootCause().isInstanceOf(jakarta.enterprise.inject.UnsatisfiedResolutionException.class));

    @Inject
    ServiceWithNoStore service;

    @Test
    void test() {
        fail("Should not be called");
    }

    @RecordChatHistory
    @RegisterAiService(chatLanguageModelSupplier = FakeChatModelSupplier.class, chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
    interface ServiceWithNoStore {
        @UserMessage("{msg}")
        String chat(String msg);
    }

    public static class FakeChatModelSupplier implements Supplier<ChatModel> {
        @Override
        public ChatModel get() {
            return new ChatModel() {
                @Override
                public ChatResponse doChat(ChatRequest chatRequest) {
                    return ChatResponse.builder().aiMessage(new AiMessage("nope")).build();
                }
            };
        }
    }
}
