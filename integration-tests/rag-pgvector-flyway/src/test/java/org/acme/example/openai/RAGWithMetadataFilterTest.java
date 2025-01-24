package org.acme.example.openai;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;

import org.acme.example.AiWithUserFilterService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@Disabled("flaky test")
public class RAGWithMetadataFilterTest {

    @Inject
    AiWithUserFilterService aiWithUserFilterService;

    private final static AtomicReference<List<ChatMessage>> lastQuery = new AtomicReference<>();

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
