package io.quarkiverse.langchain4j.runtime.aiservice;

import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.Set;

import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.service.AiServiceContext;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;

public class QuarkusAiServiceContext extends AiServiceContext {

    public ChatMemorySeeder chatMemorySeeder;
    public ImageModel imageModel;
    public Integer maxSequentialToolExecutions;

    // needed by Arc
    public QuarkusAiServiceContext() {
        super(null);
    }

    public QuarkusAiServiceContext(Class<?> aiServiceClass) {
        super(aiServiceClass);
    }

    /**
     * This is called by the {@code close} method of AiServices registered with {@link RegisterAiService}
     * when the bean's scope is closed
     */
    public void close() {
        clearChatMemory();
    }

    private void clearChatMemory() {
        if (hasChatMemory()) {
            chatMemoryService.clearAll();
        }
    }

    /**
     * This is called by the {@code remove(Object... ids)} method of AiServices when a user manually requests removal of chat
     * memories
     * via {@link io.quarkiverse.langchain4j.ChatMemoryRemover}
     */
    @SuppressWarnings("unused")
    public void removeChatMemoryIds(Object... ids) {
        if (!hasChatMemory()) {
            return;
        }
        for (Object id : ids) {
            ChatMemory chatMemory = chatMemoryService.evictChatMemory(id);
            if (chatMemory != null) {
                chatMemory.clear();
            }
        }
    }

    /**
     * Used to support the implementation of {@link dev.langchain4j.service.memory.ChatMemoryAccess#evictChatMemory(Object)}
     */
    @SuppressWarnings("unused")
    public boolean evictChatMemory(Object id) {
        if (!hasChatMemory()) {
            return false;
        }
        return chatMemoryService.evictChatMemory(id) != null;
    }

    /**
     * Used to support the implementation of {@link dev.langchain4j.service.memory.ChatMemoryAccess#getChatMemory(Object)}
     */
    @SuppressWarnings("unused")
    public ChatMemory getChatMemory(Object id) {
        if (!hasChatMemory()) {
            return null;
        }
        return chatMemoryService.getChatMemory(id);
    }

    public ChatModel effectiveChatModel(AiServiceMethodCreateInfo createInfo, Object[] methodArgs) {
        if (createInfo.getOverrideChatModelParamPosition().isPresent()) {
            // we have verified at build time that this is of type String
            return effectiveChatModel((String) methodArgs[createInfo.getOverrideChatModelParamPosition().get()]);
        }
        return chatModel;
    }

    private ChatModel effectiveChatModel(String modelName) {
        if (modelName == null) {
            // happens when @ModelName parameter exists but the caller passed null
            return chatModel;
        }
        InstanceHandle<ChatModel> instance = Arc.container().instance(ChatModel.class,
                ModelName.Literal.of(modelName));
        if (instance.isAvailable()) {
            return instance.get();
        }
        Set<String> availableNames = new HashSet<>();
        for (Instance.Handle<ChatModel> handle : Arc.container().select(ChatModel.class, Any.Literal.INSTANCE)
                .handles()) {
            Set<Annotation> qualifiers = handle.getBean().getQualifiers();
            for (Annotation qualifier : qualifiers) {
                if (qualifier.annotationType().equals(ModelName.class)) {
                    availableNames.add(((ModelName) qualifier).value());
                    break;
                }
            }
        }
        throw new IllegalStateException("No configured ChatModel named '" + modelName
                + "' was found. The application has made available the following named ChatModel instances: "
                + String.join(", ", availableNames));
    }

    public StreamingChatModel effectiveStreamingChatModel(AiServiceMethodCreateInfo createInfo, Object[] methodArgs) {
        if (createInfo.getOverrideChatModelParamPosition().isPresent()) {
            // we have verified at build time that this is of type String
            return effectiveStreamingChatModel((String) methodArgs[createInfo.getOverrideChatModelParamPosition().get()]);
        }
        return streamingChatModel;
    }

    private StreamingChatModel effectiveStreamingChatModel(String modelName) {
        if (modelName == null) {
            // happens when @ModelName parameter exists but the caller passed null
            return streamingChatModel;
        }
        InstanceHandle<StreamingChatModel> instance = Arc.container().instance(StreamingChatModel.class,
                ModelName.Literal.of(modelName));
        if (instance.isAvailable()) {
            return instance.get();
        }
        Set<String> availableNames = new HashSet<>();
        for (Instance.Handle<StreamingChatModel> handle : Arc.container().select(StreamingChatModel.class, Any.Literal.INSTANCE)
                .handles()) {
            Set<Annotation> qualifiers = handle.getBean().getQualifiers();
            for (Annotation qualifier : qualifiers) {
                if (qualifier.annotationType().equals(ModelName.class)) {
                    availableNames.add(((ModelName) qualifier).value());
                    break;
                }
            }
        }
        throw new IllegalStateException("No configured StreamingChatModel named '" + modelName
                + "' was found. The application has made available the following named StreamingChatModel instances: "
                + String.join(", ", availableNames));
    }
}
