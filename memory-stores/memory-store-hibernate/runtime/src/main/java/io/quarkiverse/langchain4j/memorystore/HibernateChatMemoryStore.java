package io.quarkiverse.langchain4j.memorystore;

import java.util.List;
import java.util.Objects;

import org.hibernate.SessionFactory;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageJsonCodec;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.quarkiverse.langchain4j.QuarkusChatMessageJsonCodecFactory;
import io.quarkiverse.langchain4j.memorystore.hibernate.runtime.model.ChatMemorySnapshot;
import io.quarkus.narayana.jta.QuarkusTransaction;

/**
 * Blocking chat memory store using short, independent ORM transactions.
 * The supplied factory belongs to Quarkus and is never closed by this store.
 * Optimistic locking detects overlapping snapshot updates, but does not serialize complete agent turns.
 */
public class HibernateChatMemoryStore implements ChatMemoryStore {
    private final SessionFactory factory;
    private final ChatMessageJsonCodec codec = new QuarkusChatMessageJsonCodecFactory().create();

    public HibernateChatMemoryStore(SessionFactory factory) {
        this.factory = Objects.requireNonNull(factory, "factory");
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String key = memoryKey(memoryId);
        String payload = QuarkusTransaction.requiringNew().call(() -> factory.fromTransaction(session -> {
            ChatMemorySnapshot snapshot = session.find(ChatMemorySnapshot.class, key);
            return snapshot == null ? null : snapshot.messagesJson();
        }));
        return payload == null ? List.of() : List.copyOf(codec.messagesFromJson(payload));
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String key = memoryKey(memoryId);
        String payload = codec.messagesToJson(List.copyOf(messages));
        QuarkusTransaction.requiringNew().run(() -> factory.inTransaction(session -> {
            ChatMemorySnapshot snapshot = session.find(ChatMemorySnapshot.class, key);
            if (snapshot == null) {
                session.persist(new ChatMemorySnapshot(key, payload));
            } else {
                snapshot.messagesJson(payload);
            }
        }));
    }

    @Override
    public void deleteMessages(Object memoryId) {
        String key = memoryKey(memoryId);
        QuarkusTransaction.requiringNew().run(() -> factory.inTransaction(session -> {
            ChatMemorySnapshot snapshot = session.find(ChatMemorySnapshot.class, key);
            if (snapshot != null) {
                session.remove(snapshot);
            }
        }));
    }

    private static String memoryKey(Object id) {
        String key = Objects.requireNonNull(Objects.requireNonNull(id, "memoryId").toString(), "memoryId.toString()");
        if (key.isEmpty() || key.length() > 255) {
            throw new IllegalArgumentException("memoryId must map to a nonempty string of at most 255 UTF-16 code units");
        }
        return key;
    }
}
