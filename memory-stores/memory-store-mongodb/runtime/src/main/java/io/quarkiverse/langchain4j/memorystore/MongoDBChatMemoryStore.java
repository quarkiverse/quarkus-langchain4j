package io.quarkiverse.langchain4j.memorystore;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.bson.Document;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.quarkiverse.langchain4j.QuarkusJsonCodecFactory;

public class MongoDBChatMemoryStore implements ChatMemoryStore {

    private static final TypeReference<List<ChatMessage>> MESSAGE_LIST_TYPE = new TypeReference<>() {
    };
    private static final String MESSAGES_FIELD = "messages";
    private static final String ID_FIELD = "_id";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final ObjectMapper MAPPER = QuarkusJsonCodecFactory.ObjectMapperHolder.MAPPER;

    private final MongoCollection<Document> collection;

    public MongoDBChatMemoryStore(MongoClient mongoClient, String database, String collection) {
        this.collection = mongoClient.getDatabase(database).getCollection(collection);
    }

    @Override
    public void deleteMessages(Object memoryId) {
        collection.deleteOne(Filters.eq(ID_FIELD, memoryId.toString()));
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        Document document = collection.find(Filters.eq(ID_FIELD, memoryId.toString())).first();
        if (document == null || !document.containsKey(MESSAGES_FIELD)) {
            return Collections.emptyList();
        }

        Object value = document.get(MESSAGES_FIELD);
        try {
            // legacy format: messages stored as a JSON string
            if (value instanceof String json) {
                return MAPPER.readValue(json, MESSAGE_LIST_TYPE);
            }
            return MAPPER.convertValue(document.getList(MESSAGES_FIELD, Document.class), MESSAGE_LIST_TYPE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        List<Document> messageDocs = messages.stream()
                .map(m -> new Document(MAPPER.convertValue(m, MAP_TYPE)))
                .toList();

        Document document = new Document()
                .append(ID_FIELD, memoryId.toString())
                .append(MESSAGES_FIELD, messageDocs);

        collection.replaceOne(
                Filters.eq(ID_FIELD, memoryId.toString()),
                document,
                new ReplaceOptions().upsert(true));
    }
}
