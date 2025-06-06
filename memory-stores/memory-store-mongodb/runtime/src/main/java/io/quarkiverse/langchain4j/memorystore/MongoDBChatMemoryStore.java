package io.quarkiverse.langchain4j.memorystore;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.List;

import org.bson.Document;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
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

        try {
            String messagesJson = document.getString(MESSAGES_FIELD);
            return QuarkusJsonCodecFactory.ObjectMapperHolder.MAPPER.readValue(
                    messagesJson, MESSAGE_LIST_TYPE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        try {
            String messagesJson = QuarkusJsonCodecFactory.ObjectMapperHolder.MAPPER.writeValueAsString(messages);
            Document document = new Document()
                    .append(ID_FIELD, memoryId.toString())
                    .append(MESSAGES_FIELD, messagesJson);

            collection.replaceOne(
                    Filters.eq(ID_FIELD, memoryId.toString()),
                    document,
                    new ReplaceOptions().upsert(true));
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }
}
