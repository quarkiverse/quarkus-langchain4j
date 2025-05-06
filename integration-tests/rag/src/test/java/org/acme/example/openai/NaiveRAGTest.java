package org.acme.example.openai;

import static io.quarkiverse.langchain4j.runtime.LangChain4jUtil.chatMessageToText;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;

import org.acme.example.AiServiceWithAutoDiscoveredRetrievalAugmentor;
import org.acme.example.AiServiceWithNoRetrievalAugmentor;
import org.acme.example.AiServiceWithSpecifiedRetrievalAugmentor;
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
public class NaiveRAGTest {

    @Inject
    AiServiceWithSpecifiedRetrievalAugmentor serviceWithSpecifiedRetrievalAugmentor;

    @Inject
    AiServiceWithAutoDiscoveredRetrievalAugmentor serviceWithAutoDiscoveredRetrievalAugmentor;

    @Inject
    AiServiceWithNoRetrievalAugmentor serviceWithNoRetrievalAugmentor;

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
    public void testWithSpecifiedAugmentor() {
        serviceWithSpecifiedRetrievalAugmentor.chat("When was Charlie born?");
        List<ChatMessage> query = lastQuery.get();
        Assertions.assertTrue(chatMessageToText(query.get(0)).contains("Charlie was born in 2018."));
    }

    @Test
    public void testWithNoAugmentor() {
        serviceWithNoRetrievalAugmentor.chat("When was Charlie born?");
        List<ChatMessage> query = lastQuery.get();
        // No RAG should be used, so nothing from the embedding store
        Assertions.assertFalse(chatMessageToText(query.get(0)).contains("Charlie was born in 2018."));
    }

    @Test
    public void testWithAutoDiscoveredAugmentor() {
        serviceWithAutoDiscoveredRetrievalAugmentor.chat("When was Charlie born?");
        List<ChatMessage> query = lastQuery.get();
        Assertions.assertTrue(chatMessageToText(query.get(0)).contains("Charlie was born in 2018."));
    }

}
