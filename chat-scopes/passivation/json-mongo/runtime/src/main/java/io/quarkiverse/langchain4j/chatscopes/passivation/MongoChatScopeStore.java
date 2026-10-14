package io.quarkiverse.langchain4j.chatscopes.passivation;

import java.io.IOException;

import org.bson.Document;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;

import io.quarkiverse.langchain4j.chatscopes.ChatScope;
import io.quarkiverse.langchain4j.chatscopes.spi.ChatScopeStore;

public class MongoChatScopeStore extends AbstractJsonChatScopeStore implements ChatScopeStore {

    private static final String CONTEXT_STATE_FIELD = "context_state";
    private static final String ID_FIELD = "_chatscope_id";

    private final MongoCollection<Document> collection;

    public MongoChatScopeStore(MongoClient mongoClient, String database, String collection) {
        this.collection = mongoClient.getDatabase(database).getCollection(collection);
    }

    final ObjectMapper mapper = new ObjectMapper();

    @Override
    protected void write(ChatScopeRepresentation chatScopeEntry) throws Exception {
        String val = mapper.writeValueAsString(chatScopeEntry);
        Document document = new Document()
                .append(ID_FIELD, chatScopeEntry.id)
                .append(CONTEXT_STATE_FIELD, val);
        collection.replaceOne(
                Filters.eq(ID_FIELD, chatScopeEntry.id),
                document,
                new ReplaceOptions().upsert(true));
    }

    @Override
    public ChatScopeRepresentation load(String chatScopeId) {
        if (chatScopeEntries.containsKey(chatScopeId)) {
            return chatScopeEntries.get(chatScopeId);
        }
        Document document = collection.find(Filters.eq(ID_FIELD, chatScopeId)).first();
        if (document == null || !document.containsKey(CONTEXT_STATE_FIELD)) {
            return null;
        }

        ChatScopeRepresentation entry = chatScopeEntries.computeIfAbsent(chatScopeId, id -> {
            try {
                String contextStateJson = document.getString(CONTEXT_STATE_FIELD);
                return mapper.readValue(contextStateJson, ChatScopeRepresentation.class);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        return entry;
    }

    @Override
    public void destroy(ChatScope scope) {
        chatScopeEntries.remove(scope.getId());
        collection.deleteOne(Filters.eq(ID_FIELD, scope.getId()));
    }

}
