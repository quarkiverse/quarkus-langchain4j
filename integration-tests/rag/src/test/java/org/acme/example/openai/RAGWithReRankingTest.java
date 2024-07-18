package org.acme.example.openai;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;

import org.acme.example.AiServiceWithReranking;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.output.Response;
import io.quarkus.logging.Log;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class RAGWithReRankingTest {

    @Inject
    AiServiceWithReranking service;

    private static AtomicReference<List<ChatMessage>> lastQuery = new AtomicReference<>();

    @BeforeAll
    public static void initializeChatModelMock() {
        // initialize the mock for the chat model
        OpenAiChatModel chatMock = Mockito.mock(OpenAiChatModel.class);
        Answer<Object> chatAnswer = invocation -> {
            Log.info("Chat model received query: " + invocation.getArgument(0));
            lastQuery.set(invocation.getArgument(0));
            return new Response<>(new AiMessage("Mock response"));
        };
        Mockito.when(chatMock.generate(Mockito.anyList())).thenAnswer(chatAnswer);
        Mockito.when(chatMock.generate(Mockito.anyList(), Mockito.anyList())).thenAnswer(chatAnswer);
        QuarkusMock.installMockForType(chatMock, OpenAiChatModel.class);
    }

    /**
     * The AI service under test uses a ContentRetriever and a ReRankingContentAggregator.
     * <p>
     * 1. The user asks 'What is the fastest car?'
     * <p>
     * 2. The content retriever retrieves two documents:
     * - "Ferrari goes 350"
     * - "Bugatti goes 450"
     * <p>
     * 3. The ReRankingContentAggregator asks the mock scoring model to score these two documents.
     * The mock scoring model returns:
     * - 0.5 for "Ferrari goes 350"
     * - 0.9 for "Bugatti goes 450"
     * <p>
     * 4. The final query goes to a mock chat model. Because the ReRankingContentAggregator was
     * configured with minScore=0.8, only "Bugatti goes 450" should be present in the final query.
     */
    @Test
    public void test() {
        service.chat("What is the fastest car?");
        String query = lastQuery.get().get(0).text();
        Assertions.assertTrue(query.contains("Bugatti goes 450"), query);
        Assertions.assertFalse(query.contains("Ferrari goes 350"), query);
    }

}
