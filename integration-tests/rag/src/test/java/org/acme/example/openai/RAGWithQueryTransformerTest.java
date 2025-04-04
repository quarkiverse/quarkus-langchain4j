package org.acme.example.openai;

import static io.quarkiverse.langchain4j.runtime.LangChain4jUtil.chatMessageToText;

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
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
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
        Answer<ChatResponse> answer = invocation -> {
            lastQuery.set(((ChatRequest) invocation.getArgument(0)).messages());
            return ChatResponse.builder().aiMessage(new AiMessage("Mock response")).build();
        };
        Mockito.when(mock.chat(Mockito.any(ChatRequest.class))).thenAnswer(answer);
        QuarkusMock.installMockForType(mock, ChatLanguageModel.class);
    }

    /**
     * The ContentRetriever will append "The transformer works!" only if
     * the QueryTransformer first transforms the query to lowercase.
     */
    @Test
    public void test() {
        service.chat("HELLO");
        String query = chatMessageToText(lastQuery.get().get(0));
        Assertions.assertTrue(query.contains("The transformer works!"), query);
    }

}
