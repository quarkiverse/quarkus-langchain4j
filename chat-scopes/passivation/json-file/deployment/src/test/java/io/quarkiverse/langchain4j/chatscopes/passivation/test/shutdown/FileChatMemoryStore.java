package io.quarkiverse.langchain4j.chatscopes.passivation.test.shutdown;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.core.type.TypeReference;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.quarkiverse.langchain4j.QuarkusJsonCodecFactory;

/**
 * This is not meant for real use only testing
 */
@ApplicationScoped
public class FileChatMemoryStore implements ChatMemoryStore {
    private static final TypeReference<List<ChatMessage>> MESSAGE_LIST_TYPE = new TypeReference<>() {
    };

    @ConfigProperty(name = "quarkus.langchain4j.memorystore.file.path", defaultValue = "/tmp/chat-memory")
    String path;

    @PostConstruct
    void init() {
        new File(path).mkdirs();
    }

    File toFile(Object memoryId) {
        return Paths.get(path, memoryId.toString().replace("#", "-")).toFile();
    }

    public void clearDisk() {
        for (File file : new File(path).listFiles()) {
            file.delete();
        }
    }

    @Override
    public synchronized List<ChatMessage> getMessages(Object memoryId) {
        File memory = toFile(memoryId);
        if (memory.exists()) {
            try {
                return QuarkusJsonCodecFactory.ObjectMapperHolder.MAPPER.readValue(memory, MESSAGE_LIST_TYPE);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return List.of();
    }

    @Override
    public synchronized void updateMessages(Object memoryId, List<ChatMessage> messages) {

        try {
            QuarkusJsonCodecFactory.ObjectMapperHolder.MAPPER.writeValue(toFile(memoryId), messages);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public synchronized void deleteMessages(Object memoryId) {
        File memoryFile = toFile(memoryId);
        if (memoryFile.exists()) {
            memoryFile.delete();
        }
    }
}
