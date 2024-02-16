package io.quarkiverse.langchain4j.runtime.devui;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.enterprise.context.control.ActivateRequestContext;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
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

    public String newConversation(String systemMessage, String message) {
        reset(systemMessage);
        return chat(message);
    }

    public String chat(String message) {
        ChatMemory memory = currentMemory.get();
        memory.add(new UserMessage(message));
        Response<AiMessage> response = model.generate(memory.messages());
        memory.add(response.content());
        return response.content().text();
    }

}
