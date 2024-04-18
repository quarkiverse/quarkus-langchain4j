package io.quarkiverse.langchain4j.openai.testing.internal;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.list;
import static org.assertj.core.api.InstanceOfAssertFactories.map;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.assertj.core.api.InstanceOfAssertFactory;
import org.assertj.core.api.ListAssert;
import org.assertj.core.api.MapAssert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;

import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import wiremock.com.fasterxml.jackson.core.type.TypeReference;
import wiremock.com.fasterxml.jackson.databind.ObjectMapper;

public abstract class OpenAiBaseTest extends WiremockAware {

    private static final TypeReference<Map<String, Object>> MAP_TYPE_REF = new TypeReference<>() {
    };

    static ObjectMapper mapper;

    @BeforeAll
    static void beforeAll() {
        mapper = new ObjectMapper();
    }

    protected void setChatCompletionMessageContent(String content) {
        ChatCompletionTransformer.setContent(content);
    }

    @AfterEach
    public void restoreOriginalChatCompletionMessageContent() {
        ChatCompletionTransformer.clearContent();
    }

    protected Map<String, Object> getRequestAsMap() throws IOException {
        return getRequestAsMap(requestBodyOfSingleRequest());
    }

    protected Map<String, Object> getRequestAsMap(byte[] body) throws IOException {
        return mapper.readValue(body, MAP_TYPE_REF);
    }

    private static final InstanceOfAssertFactory<Map, MapAssert<String, String>> MAP_STRING_STRING = map(String.class,
            String.class);
    private static final InstanceOfAssertFactory<List, ListAssert<Map>> LIST_MAP = list(Map.class);

    protected static void assertSingleRequestMessage(Map<String, Object> requestAsMap, String value) {
        assertMessages(requestAsMap, (listOfMessages -> {
            assertThat(listOfMessages).singleElement(as(MAP_STRING_STRING)).satisfies(message -> {
                assertThat(message)
                        .containsEntry("role", "user")
                        .containsEntry("content", value);
            });
        }));
    }

    protected static void assertMultipleRequestMessage(Map<String, Object> requestAsMap, List<MessageContent> messageContents) {
        assertMessages(requestAsMap, listOfMessages -> {
            assertThat(listOfMessages).asInstanceOf(LIST_MAP).hasSize(messageContents.size()).satisfies(l -> {
                for (int i = 0; i < messageContents.size(); i++) {
                    MessageContent messageContent = messageContents.get(i);
                    assertThat((Map<String, String>) l.get(i)).satisfies(message -> {
                        assertThat(message)
                                .containsEntry("role", messageContent.role());
                        if (messageContent.content() == null) {
                            if (message.containsKey("content")) {
                                assertThat(message).containsEntry("content", null);
                            }
                        } else {
                            assertThat(message).containsEntry("content", messageContent.content());
                        }

                    });
                }
            });
        });
    }

    @SuppressWarnings("rawtypes")
    protected static void assertMessages(Map<String, Object> requestAsMap, Consumer<List<? extends Map>> messagesAssertions) {
        assertThat(requestAsMap).hasEntrySatisfying("messages",
                o -> assertThat(o).asInstanceOf(list(Map.class)).satisfies(messagesAssertions));
    }

    public record MessageContent(String role, String content) {
    }

}
