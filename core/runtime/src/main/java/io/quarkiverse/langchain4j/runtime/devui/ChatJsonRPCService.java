package io.quarkiverse.langchain4j.runtime.devui;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.enterprise.context.control.ActivateRequestContext;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import io.quarkiverse.langchain4j.runtime.devui.representations.ChatMessageJson;
import io.quarkus.arc.All;

@ActivateRequestContext
public class ChatJsonRPCService {

    private final ChatLanguageModel model;

    private final ChatMemoryProvider memoryProvider;

    public ChatJsonRPCService(@All List<ChatLanguageModel> models, // don't use ChatLanguageModel model because it results in the default model not being configured
            ChatMemoryProvider memoryProvider) {
        this.model = models.get(0);
        this.memoryProvider = memoryProvider;
    }

    private final AtomicReference<ChatMemory> currentMemory = new AtomicReference<>();

    public String reset(String systemMessage) {
        if (currentMemory.get() != null) {
            currentMemory.get().clear();
        }
        ChatMemory memory = memoryProvider.get(ThreadLocalRandom.current().nextLong());
        currentMemory.set(memory);
        if (systemMessage != null && !systemMessage.isEmpty()) {
            memory.add(new SystemMessage(systemMessage));
        }
        return "OK";
    }

    public List<ChatMessageJson> newConversation(String systemMessage, String message) {
        reset(systemMessage);
        return chat(message);
    }

    public List<ChatMessageJson> chat(String message) {
        ChatMemory memory = currentMemory.get();
        // create a backup of the chat memory, because we are now going to add a new message to it,
        // and might have to remove it if it fails with an exception - but the ChatMessage API
        // doesn't allow removing messages
        List<ChatMessage> chatMemoryBackup = memory.messages();
        try {
            memory.add(new UserMessage(message));
            Response<AiMessage> modelResponse = model.generate(memory.messages());
            memory.add(modelResponse.content());
            List<ChatMessageJson> response = ChatMessageJson.listFromMemory(memory);
            Collections.reverse(response); // newest messages first
            return response;
        } catch (Exception e) {
            // restore the memory from the backup
            memory.clear();
            chatMemoryBackup.forEach(memory::add);
            throw e;
        }
    }

}
