package io.quarkiverse.langchain4j.test;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.SystemMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.ToolBox;
import io.quarkus.test.QuarkusUnitTest;

public class NoNoArgCtorSupplierTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class));
    @Inject
    TestAgent agent;

    @Test
    public void test() {
        Assertions.assertDoesNotThrow(() -> agent.chat("hello", "user-123"));
    }

    @ApplicationScoped
    @RegisterAiService(chatMemoryProvider = NoNoArgChatMemoryProvider.class)
    public interface TestAgent {

        // Basic chat method
        @SystemMessage("You are a simple agent that can call Tools.")
        @ToolBox({ MyTools.class })
        Result<String> chat(String message, @MemoryId String memoryId);

    }

    @ApplicationScoped
    public static class NoNoArgChatMemoryProvider implements ChatMemoryProvider {

        public NoNoArgChatMemoryProvider(SomeBean something) { // no no-arg ctor
        }

        @Override
        public dev.langchain4j.memory.ChatMemory get(Object memoryId) {
            return MessageWindowChatMemory.withMaxMessages(10);
        }
    }

    @Singleton
    public static class SomeBean {

    }

    @ApplicationScoped
    public static class MyTools {

        @Tool
        public String tool(String input) {
            return "result: " + input;
        }
    }

    @ApplicationScoped
    public static class MyChatModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest request) {
            return ChatResponse.builder().aiMessage(new AiMessage("hello")).build();
        }
    }
}
