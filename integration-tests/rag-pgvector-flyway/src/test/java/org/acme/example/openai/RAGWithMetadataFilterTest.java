package org.acme.example.openai;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;

import org.acme.example.AiWithUserFilterService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@EnabledIfSystemProperty(named = "enable.flaky-tests", matches = "true")
public class RAGWithMetadataFilterTest {

    @Inject
    AiWithUserFilterService aiWithUserFilterService;

    private final static AtomicReference<List<ChatMessage>> lastQuery = new AtomicReference<>();

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
    public void testWithUser1Filter() {
        aiWithUserFilterService.chat("1", "When was Charlie born?");
        List<ChatMessage> query = lastQuery.get();
        UserMessage userMessage = (UserMessage) query.get(0);
        Assertions.assertTrue(userMessage.singleText().contains("Charlie was born in 2018."));
    }

    @Test
    public void testWithUser2Filter() {
        aiWithUserFilterService.chat("2", "When was Charlie born?");
        List<ChatMessage> query = lastQuery.get();
        UserMessage userMessage = (UserMessage) query.get(0);
        Assertions.assertTrue(userMessage.singleText().contains("Charlie was born in 2015."));
    }

}
