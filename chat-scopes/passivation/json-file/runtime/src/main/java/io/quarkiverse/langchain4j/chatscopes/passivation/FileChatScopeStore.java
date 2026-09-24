package io.quarkiverse.langchain4j.chatscopes.passivation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.langchain4j.chatscopes.ChatScope;
import io.quarkiverse.langchain4j.chatscopes.spi.ChatScopeStore;
import io.quarkus.runtime.Startup;

@ApplicationScoped
public class FileChatScopeStore extends AbstractJsonChatScopeStore implements ChatScopeStore {
    @ConfigProperty(name = "quarkiverse.langchain4j.chatscopes.passivation.file.path", defaultValue = "/tmp/chatscopes")
    String path;

    Path dir;

    ObjectMapper mapper = new ObjectMapper();

    @Startup
    public void init() throws Exception {
        dir = Path.of(this.path);
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }
    }

    @Override
    protected void write(ChatScopeRepresentation chatScopeEntry) throws Exception {
        Path chatScopePath = scopePath(chatScopeEntry.id);
        mapper.writeValue(chatScopePath.toFile(), chatScopeEntry);
    }

    Path scopePath(String id) {
        return dir.resolve(id + ".json");
    }

    @Override
    public ChatScopeRepresentation load(String chatScopeId) {
        if (chatScopeEntries.containsKey(chatScopeId)) {
            return chatScopeEntries.get(chatScopeId);
        }
        Path chatScopePath = scopePath(chatScopeId);
        if (!Files.exists(chatScopePath)) {
            return null;
        }

        ChatScopeRepresentation entry = chatScopeEntries.computeIfAbsent(chatScopeId, id -> {
            try {
                return mapper.readValue(chatScopePath.toFile(),
                        ChatScopeRepresentation.class);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        return entry;
    }

    @Override
    public void destroy(ChatScope scope) {
        chatScopeEntries.remove(scope.getId());
        Path chatScopePath = scopePath(scope.getId());
        if (Files.exists(chatScopePath)) {
            try {
                Files.delete(chatScopePath);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

}
