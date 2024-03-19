package io.quarkiverse.langchain4j.runtime.devui;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import jakarta.enterprise.context.control.ActivateRequestContext;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.query.Metadata;
import io.quarkiverse.langchain4j.runtime.devui.json.ChatMessagePojo;
import io.quarkiverse.langchain4j.runtime.devui.json.ChatResultPojo;
import io.quarkus.arc.All;
import io.quarkus.logging.Log;

@ActivateRequestContext
public class ChatJsonRPCService {

    private final ChatLanguageModel model;

    private final ChatMemoryProvider memoryProvider;

    // The augmentor to use, if any is found in the application. Only augmentors that are CDI beans
    // can be found. If more than one is found, for now we choose the first one offered by the CDI container.
    // FIXME: perhaps the UI could offer choosing between available augmentors when there are more
    private RetrievalAugmentor retrievalAugmentor;

    public ChatJsonRPCService(@All List<ChatLanguageModel> models, // don't use ChatLanguageModel model because it results in the default model not being configured
            @All List<Supplier<RetrievalAugmentor>> retrievalAugmentorSuppliers,
            @All List<RetrievalAugmentor> retrievalAugmentors,
            ChatMemoryProvider memoryProvider) {
        this.model = models.get(0);
        this.retrievalAugmentor = null;
        for (Supplier<RetrievalAugmentor> supplier : retrievalAugmentorSuppliers) {
            this.retrievalAugmentor = supplier.get();
            if (this.retrievalAugmentor != null) {
                break;
            }
        }
        if (this.retrievalAugmentor == null) {
            for (RetrievalAugmentor augmentorFromCdi : retrievalAugmentors) {
                this.retrievalAugmentor = augmentorFromCdi;
                if (this.retrievalAugmentor != null) {
                    break;
                }
            }
        }
        this.memoryProvider = memoryProvider;
    }

    private final AtomicReference<ChatMemory> currentMemory = new AtomicReference<>();
    private final AtomicLong currentMemoryId = new AtomicLong();

    public String reset(String systemMessage) {
        if (currentMemory.get() != null) {
            currentMemory.get().clear();
        }
        long memoryId = ThreadLocalRandom.current().nextLong();
        currentMemoryId.set(memoryId);
        ChatMemory memory = memoryProvider.get(memoryId);
        currentMemory.set(memory);
        if (systemMessage != null && !systemMessage.isEmpty()) {
            memory.add(new SystemMessage(systemMessage));
        }
        return "OK";
    }

    public ChatResultPojo newConversation(String systemMessage, String message) {
        reset(systemMessage);
        return chat(message);
    }

    public ChatResultPojo chat(String message) {
        ChatMemory memory = currentMemory.get();
        if (memory == null) {
            reset("");
            memory = currentMemory.get();
        }
        // create a backup of the chat memory, because we are now going to
        // add a new message to it, and might have to remove it if the chat
        // request fails - unfortunately the ChatMemory API doesn't allow
        // removing single messages
        List<ChatMessage> chatMemoryBackup = memory.messages();
        try {
            if (retrievalAugmentor != null) {
                UserMessage userMessage = UserMessage.from(message);
                Metadata metadata = Metadata.from(userMessage, currentMemoryId.get(), memory.messages());
                memory.add(retrievalAugmentor.augment(userMessage, metadata));
            } else {
                memory.add(new UserMessage(message));
            }
            Response<AiMessage> modelResponse = model.generate(memory.messages());
            memory.add(modelResponse.content());
            List<ChatMessagePojo> response = ChatMessagePojo.listFromMemory(memory);
            return new ChatResultPojo(response, null);
        } catch (Throwable t) {
            // restore the memory from the backup
            memory.clear();
            chatMemoryBackup.forEach(memory::add);
            Log.warn(t);
            return new ChatResultPojo(null, t.getMessage());
        }
    }

}
