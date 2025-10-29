package org.acme.example.openai;

import static io.quarkiverse.langchain4j.runtime.LangChain4jUtil.chatMessageToText;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;

import org.acme.example.AiServiceWithQueryRouterAndContentInjector;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class RAGWithQueryRouterAndContentInjectorTest {

    @Inject
    AiServiceWithQueryRouterAndContentInjector service;

    private static AtomicReference<List<ChatMessage>> lastQuery = new AtomicReference<>();

    @BeforeAll
    public static void initializeModel() {
        ChatModel mock = Mockito.mock(ChatModel.class);
        Answer<ChatResponse> answer = invocation -> {
            lastQuery.set(((ChatRequest) invocation.getArgument(0)).messages());
            return ChatResponse.builder().aiMessage(new AiMessage("Mock response")).build();
        };
        Mockito.when(mock.chat(Mockito.any(ChatRequest.class))).thenAnswer(answer);
        QuarkusMock.installMockForType(mock, ChatModel.class);
    }

    @Test
    public void test() {
        service.chat("What dogs do?");
        String query = chatMessageToText(lastQuery.get().get(0));
        Assertions.assertTrue(query.equals("What dogs do? - Dogs bark"), query);
    }

}
