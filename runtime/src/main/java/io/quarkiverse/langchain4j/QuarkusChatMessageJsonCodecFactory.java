package io.quarkiverse.langchain4j;

import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageJsonCodec;
import dev.langchain4j.spi.data.message.ChatMessageJsonCodecFactory;

public class QuarkusChatMessageJsonCodecFactory implements ChatMessageJsonCodecFactory {
    @Override
    public ChatMessageJsonCodec create() {
        return new Codec();
    }

    private static class Codec implements ChatMessageJsonCodec {

        private static final TypeReference<List<ChatMessage>> MESSAGE_LIST_TYPE = new TypeReference<>() {
        };

        @Override
        public ChatMessage messageFromJson(String json) {
            try {
                return QuarkusJsonCodecFactory.ObjectMapperHolder.MAPPER.readValue(json, ChatMessage.class);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public List<ChatMessage> messagesFromJson(String json) {
            if (json == null) {
                return Collections.emptyList();
            }
            try {
                return QuarkusJsonCodecFactory.ObjectMapperHolder.MAPPER.readValue(json, MESSAGE_LIST_TYPE);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public String messageToJson(ChatMessage message) {
            try {
                return QuarkusJsonCodecFactory.ObjectMapperHolder.MAPPER.writeValueAsString(message);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public String messagesToJson(List<ChatMessage> messages) {
            try {
                return QuarkusJsonCodecFactory.ObjectMapperHolder.MAPPER.writeValueAsString(messages);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
