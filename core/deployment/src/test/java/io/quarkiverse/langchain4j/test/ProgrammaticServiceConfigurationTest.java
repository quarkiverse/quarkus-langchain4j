package io.quarkiverse.langchain4j.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Supplier;

import jakarta.enterprise.context.control.ActivateRequestContext;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.runtime.aiservice.NoopChatMemory;
import io.quarkus.test.QuarkusUnitTest;

public class ProgrammaticServiceConfigurationTest {

    private static final String SYSTEM_MESSAGE = "You are an AI assistant that always answers in rhymes.";

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(AssistantService.class));

    @Test
    @ActivateRequestContext
    public void serviceUsesProgrammaticSystemMessage() {
        AssistantService assistantService = createAssistantService();
        assertThat(assistantService.echoSystemMessage("test")).isEqualTo(SYSTEM_MESSAGE);
    }

    private AssistantService createAssistantService() {
        return AiServices.builder(AssistantService.class)
                .chatModel(new MyChatModel())
                .chatMemoryProvider(memoryId -> new NoopChatMemory())
                .systemMessageProvider(memoryId -> SYSTEM_MESSAGE)
                .build();
    }

    @RegisterAiService(chatLanguageModelSupplier = MyChatModelSupplier.class)
    public interface AssistantService {

        String echoSystemMessage(@UserMessage String userMessage);
    }

    public static class MyChatModelSupplier implements Supplier<ChatModel> {

        @Override
        public ChatModel get() {
            return new MyChatModel();
        }
    }

    public static class MyChatModel implements ChatModel {

        @Override
        public ChatResponse chat(List<ChatMessage> messages) {
            throw new UnsupportedOperationException("Should not be called");
        }

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            SystemMessage systemMessage = chatRequest.messages().stream().filter(SystemMessage.class::isInstance)
                    .map(SystemMessage.class::cast).findFirst().orElse(null);
            return ChatResponse.builder().aiMessage(new AiMessage(systemMessage.text())).build();
        }
    }
}
