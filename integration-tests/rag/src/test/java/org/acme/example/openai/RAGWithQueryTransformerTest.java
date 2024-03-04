package org.acme.example.openai;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;

import org.acme.example.AiServiceWithQueryTransformer;
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
public class RAGWithQueryTransformerTest {

    @Inject
    AiServiceWithQueryTransformer service;

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

    /**
     * The ContentRetriever will append "The transformer works!" only if
     * the QueryTransformer first transforms the query to lowercase.
     */
    @Test
    public void test() {
        service.chat("HELLO");
        String query = lastQuery.get().get(0).text();
        Assertions.assertTrue(query.contains("The transformer works!"), query);
    }

}
