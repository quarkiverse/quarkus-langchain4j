package org.acme.examples.aiservices;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.list;
import static org.assertj.core.api.InstanceOfAssertFactories.map;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.assertj.core.api.InstanceOfAssertFactory;
import org.assertj.core.api.ListAssert;
import org.assertj.core.api.MapAssert;

import com.fasterxml.jackson.core.type.TypeReference;

class MessageAssertUtils {

    static final TypeReference<Map<String, Object>> MAP_TYPE_REF = new TypeReference<>() {
    };
    private static final InstanceOfAssertFactory<Map, MapAssert<String, String>> MAP_STRING_STRING = map(String.class,
            String.class);
    private static final InstanceOfAssertFactory<List, ListAssert<Map>> LIST_MAP = list(Map.class);

    static void assertSingleRequestMessage(Map<String, Object> requestAsMap, String value) {
        assertMessages(requestAsMap, (listOfMessages -> {
            assertThat(listOfMessages).singleElement(as(MAP_STRING_STRING)).satisfies(message -> {
                assertThat(message)
                        .containsEntry("role", "user")
                        .containsEntry("content", value);
            });
        }));
    }

    static void assertMultipleRequestMessage(Map<String, Object> requestAsMap, List<MessageContent> messageContents) {
        assertMessages(requestAsMap, listOfMessages -> {
            assertThat(listOfMessages).asInstanceOf(LIST_MAP).hasSize(messageContents.size()).satisfies(l -> {
                for (int i = 0; i < messageContents.size(); i++) {
                    MessageContent messageContent = messageContents.get(i);
                    assertThat((Map<String, String>) l.get(i)).satisfies(message -> {
                        assertThat(message)
                                .containsEntry("role", messageContent.getRole());
                        if (messageContent.getContent() == null) {
                            if (message.containsKey("content")) {
                                assertThat(message).containsEntry("content", null);
                            }
                        } else {
                            assertThat(message).containsEntry("content", messageContent.getContent());
                        }

                    });
                }
            });
        });
    }

    @SuppressWarnings("rawtypes")
    static void assertMessages(Map<String, Object> requestAsMap, Consumer<List<? extends Map>> messagesAssertions) {
        assertThat(requestAsMap).hasEntrySatisfying("messages",
                o -> assertThat(o).asInstanceOf(list(Map.class)).satisfies(messagesAssertions));
    }

    static class MessageContent {
        private final String role;
        private final String content;

        public MessageContent(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() {
            return role;
        }

        public String getContent() {
            return content;
        }
    }
}
