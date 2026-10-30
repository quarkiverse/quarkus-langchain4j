package io.quarkiverse.langchain4j.runtime.aiservice;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.service.AiServiceContext;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolService;
import dev.langchain4j.service.tool.search.ToolSearchService;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.runtime.config.AiServiceConfig;
import io.quarkiverse.langchain4j.runtime.types.TypeUtil;
import io.quarkiverse.langchain4j.spi.DefaultMemoryIdProvider;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;

public class QuarkusAiServiceContext extends AiServiceContext {

    public ChatMemorySeeder chatMemorySeeder;
    public ThinkingHandler thinkingHandler;
    public ImageModel imageModel;
    public Integer maxToolCallingRoundTrips;
    public Integer maxToolCallsPerResponse;
    public boolean allowContinuousForcedToolCalling;
    /**
     * Whether the tools are executed concurrently, using the executor of the {@link ToolService}
     */
    public boolean executeToolsConcurrently;
    public DefaultMemoryIdProvider defaultMemoryIdProvider;
    public ChatMemoryFlushStrategy chatMemoryFlushStrategy = ChatMemoryFlushStrategy.DEFERRED;
    public ToolSearchService toolSearchService;
    public List<String> classLevelSkillNames;

    private final Map<AiServiceMethodCreateInfo, RetrievalAugmentor> methodRetrievalAugmentors = new ConcurrentHashMap<>();

    private boolean hasChatMemoryProvider;

    // needed by Arc
    public QuarkusAiServiceContext() {
        super(null);
        this.toolService = new ToolService();
        this.toolService.maxToolCallingRoundTrips(AiServiceConfig.DEFAULT_MAX_TOOL_CALLING_ROUND_TRIPS);
    }

    public QuarkusAiServiceContext(Class<?> aiServiceClass) {
        super(aiServiceClass);
        this.toolService = new ToolService();
        this.toolService.maxToolCallingRoundTrips(AiServiceConfig.DEFAULT_MAX_TOOL_CALLING_ROUND_TRIPS);
    }

    public void setMaxToolCallingRoundTrips(int value) {
        this.maxToolCallingRoundTrips = value;
        this.toolService.maxToolCallingRoundTrips(value);
    }

    /**
     * Tracks whether a {@link ChatMemoryProvider} (as opposed to a single shared {@link ChatMemory}) backs this
     * context. The {@link DefaultMemoryIdProvider} chain must only be consulted when a provider is configured;
     * consulting it for a single shared memory yields a non-default memory id that the single-memory
     * {@code ChatMemoryService} cannot resolve, leading to a {@link NullPointerException} when the service is
     * invoked inside an active request scope.
     */
    public boolean hasChatMemoryProvider() {
        return hasChatMemoryProvider;
    }

    @Override
    public void initChatMemories(ChatMemory chatMemory) {
        super.initChatMemories(chatMemory);
        this.hasChatMemoryProvider = false;
    }

    @Override
    public void initChatMemories(ChatMemoryProvider chatMemoryProvider) {
        super.initChatMemories(chatMemoryProvider);
        this.hasChatMemoryProvider = true;
    }

    /**
     * This is called by the {@code close} method of AiServices registered with {@link RegisterAiService}
     * when the bean's scope is closed
     */
    public void close() {
        clearChatMemory();
    }

    public void clearChatMemory() {
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
     * This is called by the {@code manageChatMemory(Object... ids)} method of AiServices when a user manually requests that
     * the AI service manage the lifecycle of chat memories provided
     * via {@link io.quarkiverse.langchain4j.ChatMemoryRemover}
     */
    @SuppressWarnings("unused")
    public void manageChatMemoryIds(Object... ids) {
        if (!hasChatMemory()) {
            return;
        }
        for (Object id : ids) {
            chatMemoryService.getOrCreateChatMemory(id);
        }
    }

    @SuppressWarnings("unused")
    public Collection<Object> getAllChatMemoryIds() {
        if (!hasChatMemory()) {
            return Collections.emptyList();
        }
        return chatMemoryService.getChatMemoryIDs();
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

    /**
     * Returns the {@code @AugmentWith} augmentor of the given method, created once per AI service instance,
     * the same lifecycle as the class-level augmentor.
     */
    public RetrievalAugmentor methodRetrievalAugmentor(AiServiceMethodCreateInfo createInfo) {
        RetrievalAugmentor augmentor = methodRetrievalAugmentors.get(createInfo);
        if (augmentor != null) {
            return augmentor;
        }
        augmentor = createInfo.createMethodRetrievalAugmentor();
        if (augmentor == null) {
            return null;
        }
        RetrievalAugmentor previous = methodRetrievalAugmentors.putIfAbsent(createInfo, augmentor);
        return previous != null ? previous : augmentor;
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
        return unwrappedStreamingChatModel();
    }

    /**
     * Makes the upstream LangChain4j {@code AiServiceTokenStream} execute tools on the thread they require when a
     * {@code TokenStream} method of this AI service has tools that cannot run on an event loop, or when this AI service
     * has a {@linkplain ToolProvider#isDynamic() dynamic} tool provider, which is re-evaluated after the tools are
     * executed and may block. The upstream
     * implementation does not pass the method to the model, so the wrapped model is used by every {@code TokenStream}
     * method of the AI service, while the {@code Multi} methods, implemented in Quarkus, keep using the delegate.
     */
    public void dispatchTokenStreamToolExecution(Collection<AiServiceMethodCreateInfo> methodCreateInfos) {
        if (streamingChatModel == null || streamingChatModel instanceof ToolExecutionDispatchingStreamingChatModel) {
            return;
        }
        List<AiServiceMethodCreateInfo> tokenStreamMethodCreateInfos = new ArrayList<>();
        boolean switchToWorkerThreadForToolExecution = false;
        for (AiServiceMethodCreateInfo methodCreateInfo : methodCreateInfos) {
            if (TypeUtil.isTokenStream(methodCreateInfo.getReturnType())) {
                tokenStreamMethodCreateInfos.add(methodCreateInfo);
                switchToWorkerThreadForToolExecution |= methodCreateInfo.isSwitchToWorkerThreadForToolExecution();
            }
        }
        if (switchToWorkerThreadForToolExecution || (!tokenStreamMethodCreateInfos.isEmpty() && hasDynamicToolProvider())) {
            streamingChatModel = new ToolExecutionDispatchingStreamingChatModel(streamingChatModel, this,
                    tokenStreamMethodCreateInfos);
        }
    }

    /**
     * @return whether one of the tool providers of this AI service is {@linkplain ToolProvider#isDynamic() dynamic}
     */
    boolean hasDynamicToolProvider() {
        for (ToolProvider toolProvider : toolService.toolProviders()) {
            if (toolProvider.isDynamic()) {
                return true;
            }
        }
        return false;
    }

    private StreamingChatModel unwrappedStreamingChatModel() {
        if (streamingChatModel instanceof ToolExecutionDispatchingStreamingChatModel wrapper) {
            return wrapper.delegate();
        }
        return streamingChatModel;
    }

    private StreamingChatModel effectiveStreamingChatModel(String modelName) {
        if (modelName == null) {
            // happens when @ModelName parameter exists but the caller passed null
            return unwrappedStreamingChatModel();
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
