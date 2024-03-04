package org.acme.example.openai;

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
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
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
        ChatLanguageModel mock = Mockito.mock(ChatLanguageModel.class);
        Answer<Object> answer = invocation -> {
            lastQuery.set(invocation.getArgument(0));
            return new Response<>(new AiMessage("Mock response"));
        };
        Mockito.when(mock.generate(Mockito.anyList())).thenAnswer(answer);
        Mockito.when(mock.generate(Mockito.anyList(), Mockito.anyList())).thenAnswer(answer);
        QuarkusMock.installMockForType(mock, ChatLanguageModel.class);
    }

    @Test
    public void testWithSpecifiedAugmentor() {
        serviceWithSpecifiedRetrievalAugmentor.chat("When was Charlie born?");
        List<ChatMessage> query = lastQuery.get();
        Assertions.assertTrue(query.get(0).text().contains("Charlie was born in 2018."));
    }

    @Test
    public void testWithNoAugmentor() {
        serviceWithNoRetrievalAugmentor.chat("When was Charlie born?");
        List<ChatMessage> query = lastQuery.get();
        // No RAG should be used, so nothing from the embedding store
        Assertions.assertFalse(query.get(0).text().contains("Charlie was born in 2018."));
    }

    @Test
    public void testWithAutoDiscoveredAugmentor() {
        serviceWithAutoDiscoveredRetrievalAugmentor.chat("When was Charlie born?");
        List<ChatMessage> query = lastQuery.get();
        Assertions.assertTrue(query.get(0).text().contains("Charlie was born in 2018."));
    }

}
