package io.quarkiverse.langchain4j.runtime;

import static io.quarkiverse.langchain4j.QuarkusAiServicesFactory.InstanceHolder.INSTANCE;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.enterprise.util.TypeLiteral;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.service.tool.ToolArgumentsErrorHandler;
import dev.langchain4j.service.tool.ToolExecutionErrorHandler;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.search.ToolSearchService;
import dev.langchain4j.service.tool.search.ToolSearchStrategy;
import io.quarkiverse.langchain4j.DefaultToolExecutionErrorHandler;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.observability.AiServiceEvents;
import io.quarkiverse.langchain4j.runtime.aiservice.AiServiceClassCreateInfo;
import io.quarkiverse.langchain4j.runtime.aiservice.AiServiceMethodCreateInfo;
import io.quarkiverse.langchain4j.runtime.aiservice.ChatMemoryFlushStrategy;
import io.quarkiverse.langchain4j.runtime.aiservice.ChatMemorySeeder;
import io.quarkiverse.langchain4j.runtime.aiservice.DeclarativeAiServiceCreateInfo;
import io.quarkiverse.langchain4j.runtime.aiservice.QuarkusAiServiceContext;
import io.quarkiverse.langchain4j.runtime.aiservice.SystemMessageProvider;
import io.quarkiverse.langchain4j.runtime.aiservice.SystemMessageProviderWithContext;
import io.quarkiverse.langchain4j.runtime.aiservice.ThinkingHandler;
import io.quarkiverse.langchain4j.runtime.tool.LoggingToolExecutionErrorHandler;
import io.quarkiverse.langchain4j.spi.DefaultMemoryIdProvider;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class AiServicesRecorder {
    private static final TypeLiteral<Instance<RetrievalAugmentor>> RETRIEVAL_AUGMENTOR_TYPE_LITERAL = new TypeLiteral<>() {
    };

    private static final TypeLiteral<Instance<ToolProvider>> TOOL_PROVIDER_TYPE_LITERAL = new TypeLiteral<>() {
    };

    private static final TypeLiteral<Instance<ToolSearchStrategy>> TOOL_SEARCH_STRATEGY_TYPE_LITERAL = new TypeLiteral<>() {
    };

    // the key is the interface's class name
    private static final Map<String, AiServiceClassCreateInfo> metadata = new HashMap<>();

    public void setMetadata(Map<String, AiServiceClassCreateInfo> metadata) {
        AiServicesRecorder.metadata.putAll(metadata);
    }

    public static Map<String, AiServiceClassCreateInfo> getMetadata() {
        return metadata;
    }

    public static void clearMetadata() {
        metadata.clear();
    }

    @SuppressWarnings("unused") // called from the implementation of each AiService method
    public static AiServiceMethodCreateInfo getAiServiceMethodCreateInfo(String className, String methodId) {
        AiServiceClassCreateInfo classCreateInfo = metadata.get(className);
        if (classCreateInfo == null) {
            throw new RuntimeException("Quarkus was not able to determine class '" + className
                    + "' as an AiService at build time. Consider annotating the clas with @CreatedAware");
        }
        AiServiceMethodCreateInfo methodCreateInfo = classCreateInfo.methodMap().get(methodId);
        if (methodCreateInfo == null) {
            throw new IllegalStateException("Unable to locate method metadata for descriptor '" + methodId
                    + "'. Please report this issue to the maintainers");
        }
        populateToolMetadata(methodCreateInfo);
        return methodCreateInfo;
    }

    private static void populateToolMetadata(AiServiceMethodCreateInfo methodCreateInfo) {
        Map<String, AnnotationLiteral<?>> toolsClassInfo = methodCreateInfo.getToolClassInfo();
        if ((toolsClassInfo != null) && !toolsClassInfo.isEmpty()) {
            // we need to make sure we populate toolSpecifications and toolExecutors only the first time the method is called
            if (methodCreateInfo.getToolSpecifications().isEmpty()) {
                synchronized (methodCreateInfo.getToolSpecifications()) {
                    if (methodCreateInfo.getToolSpecifications().isEmpty()) {
                        try {
                            List<Object> objectWithTools = new ArrayList<>(toolsClassInfo.size());
                            for (var entry : toolsClassInfo.entrySet()) {
                                AnnotationLiteral<?> qualifier = entry.getValue();
                                Object tool;
                                if (qualifier != null) {
                                    tool = Arc.container().instance(
                                            Thread.currentThread().getContextClassLoader().loadClass(entry.getKey()),
                                            qualifier).get();
                                } else {
                                    tool = Arc.container().instance(
                                            Thread.currentThread().getContextClassLoader().loadClass(entry.getKey())).get();
                                }
                                if (tool == null) {
                                    throw new IllegalStateException("Unknown tool: " + entry.getKey());
                                }
                                objectWithTools.add(tool);
                            }
                            ToolsRecorder.populateToolMetadata(objectWithTools, methodCreateInfo.getToolSpecifications(),
                                    methodCreateInfo.getToolExecutors());
                        } catch (ClassNotFoundException e) {
                            throw new IllegalStateException(e);
                        }
                    }
                }
            }

        }
    }

    public Function<SyntheticCreationalContext<QuarkusAiServiceContext>, QuarkusAiServiceContext> createDeclarativeAiService(
            DeclarativeAiServiceCreateInfo info) {
        return new Function<>() {
            @SuppressWarnings("unchecked")
            @Override
            public QuarkusAiServiceContext apply(SyntheticCreationalContext<QuarkusAiServiceContext> creationalContext) {
                try {
                    Class<?> serviceClass = loadClass(info.serviceClassName());

                    QuarkusAiServiceContext aiServiceContext = new QuarkusAiServiceContext(serviceClass);
                    if (info.defaultMemoryIdProviderClassName() != null) {
                        aiServiceContext.defaultMemoryIdProvider = (DefaultMemoryIdProvider) Thread
                                .currentThread().getContextClassLoader().loadClass(info.defaultMemoryIdProviderClassName())
                                .getConstructor().newInstance();
                    }

                    // we don't really care about QuarkusAiServices here, all we care about is that it
                    // properly populates QuarkusAiServiceContext which is what we are trying to construct
                    var quarkusAiServices = INSTANCE.create(aiServiceContext);

                    // Populate the AI service listeners
                    Arrays.stream(AiServiceEvents.values())
                            .map(event -> event.createListener(serviceClass))
                            .forEach(quarkusAiServices::registerListener);

                    // Chat model — via CDI
                    if (NamedConfigUtil.isDefault(info.chatModelName())) {
                        if (info.needsChatModel()) {
                            quarkusAiServices
                                    .chatModel(creationalContext.getInjectedReference(ChatModel.class));
                        }
                        if (info.needsStreamingChatModel()) {
                            quarkusAiServices
                                    .streamingChatModel(
                                            creationalContext.getInjectedReference(StreamingChatModel.class));
                        }
                    } else {
                        if (info.needsChatModel()) {
                            quarkusAiServices.chatModel(creationalContext.getInjectedReference(ChatModel.class,
                                    ModelName.Literal.of(info.chatModelName())));
                        }
                        if (info.needsStreamingChatModel()) {
                            quarkusAiServices.streamingChatModel(
                                    creationalContext.getInjectedReference(StreamingChatModel.class,
                                            ModelName.Literal.of(info.chatModelName())));
                        }
                    }

                    // Tools
                    Map<String, AnnotationLiteral<?>> toolsClasses = info.toolsClassInfo();
                    boolean hasExplicitTools = (toolsClasses != null) && !toolsClasses.isEmpty();
                    if (hasExplicitTools) {
                        List<Object> tools = new ArrayList<>(toolsClasses.size());
                        for (var entry : toolsClasses.entrySet()) {
                            AnnotationLiteral<?> qualifier = entry.getValue();
                            Object tool;
                            if (qualifier != null) {
                                tool = creationalContext.getInjectedReference(
                                        loadClass(entry.getKey()),
                                        qualifier);
                            } else {
                                tool = creationalContext.getInjectedReference(
                                        loadClass(entry.getKey()));
                            }
                            if (tool == null) {
                                throw new IllegalStateException("Unknown tool: " + entry.getKey());
                            }
                            tools.add(tool);
                        }
                        quarkusAiServices.tools(tools);
                    }

                    // Tool hallucination strategy
                    DeclarativeAiServiceCreateInfo.ComponentEntry toolHallucinationEntry = info.toolHallucinationStrategy();
                    switch (toolHallucinationEntry.mode()) {
                        case EXPLICIT -> {
                            Object toolHallucinationStrategy = creationalContext.getInjectedReference(
                                    loadClass(toolHallucinationEntry.className()));
                            if (toolHallucinationStrategy == null) {
                                throw new IllegalStateException(
                                        "Unknown tool hallucination strategy: " + toolHallucinationEntry.className());
                            }
                            quarkusAiServices.toolHallucinationStrategy(toolHallucinationStrategy);
                        }
                        case AUTO_DISCOVER, SKIP -> {
                        }
                    }

                    if (info.toolArgumentsErrorHandlerClassName() != null) {
                        ToolArgumentsErrorHandler toolArgumentsErrorHandler = (ToolArgumentsErrorHandler) creationalContext
                                .getInjectedReference(
                                        loadClass(info.toolArgumentsErrorHandlerClassName()));
                        quarkusAiServices.toolArgumentsErrorHandler(toolArgumentsErrorHandler);
                    }

                    if (info.toolExecutionErrorHandlerClassName() != null) {
                        ToolExecutionErrorHandler toolExecutionErrorHandler = (ToolExecutionErrorHandler) creationalContext
                                .getInjectedReference(
                                        loadClass(info.toolExecutionErrorHandlerClassName()));
                        quarkusAiServices.toolExecutionErrorHandler(toolExecutionErrorHandler);
                    } else {
                        InstanceHandle<ToolExecutionErrorHandler> instance = Arc.container()
                                .instance(ToolExecutionErrorHandler.class, DefaultToolExecutionErrorHandler.Literal.INSTANCE);
                        if (instance.isAvailable()) {
                            quarkusAiServices.toolExecutionErrorHandler(instance.get());
                        } else {
                            quarkusAiServices.toolExecutionErrorHandler(new LoggingToolExecutionErrorHandler());
                        }
                    }

                    // Tool provider
                    DeclarativeAiServiceCreateInfo.ComponentEntry toolProviderEntry = info.toolProvider();
                    switch (toolProviderEntry.mode()) {
                        case AUTO_DISCOVER -> {
                            Instance<ToolProvider> instance = creationalContext
                                    .getInjectedReference(TOOL_PROVIDER_TYPE_LITERAL);
                            // if the service has explicit tools and auto-discover,
                            // give priority to the explicit tools, don't throw an error
                            if (instance.isResolvable() && !hasExplicitTools) {
                                quarkusAiServices.toolProvider(instance.get());
                            }
                        }
                        case EXPLICIT -> {
                            ToolProvider toolProvider = (ToolProvider) creationalContext
                                    .getInjectedReference(loadClass(toolProviderEntry.className()));
                            quarkusAiServices.toolProvider(toolProvider);
                        }
                        case SKIP -> {
                        }
                    }

                    // Tool search strategy
                    DeclarativeAiServiceCreateInfo.ComponentEntry toolSearchStrategyEntry = info.toolSearchStrategy();
                    switch (toolSearchStrategyEntry.mode()) {
                        case AUTO_DISCOVER -> {
                            Instance<ToolSearchStrategy> instance = creationalContext
                                    .getInjectedReference(TOOL_SEARCH_STRATEGY_TYPE_LITERAL);
                            if (instance.isResolvable()) {
                                aiServiceContext.toolSearchService = new ToolSearchService(instance.get());
                            }
                        }
                        case EXPLICIT -> {
                            ToolSearchStrategy toolSearchStrategy = (ToolSearchStrategy) creationalContext
                                    .getInjectedReference(loadClass(toolSearchStrategyEntry.className()));
                            aiServiceContext.toolSearchService = new ToolSearchService(toolSearchStrategy);
                        }
                        case SKIP -> {
                        }
                    }

                    // Chat memory provider
                    DeclarativeAiServiceCreateInfo.ComponentEntry chatMemoryProviderEntry = info.chatMemoryProvider();
                    switch (chatMemoryProviderEntry.mode()) {
                        case AUTO_DISCOVER -> {
                            quarkusAiServices.chatMemoryProvider(creationalContext.getInjectedReference(
                                    ChatMemoryProvider.class));
                        }
                        case EXPLICIT -> {
                            ChatMemoryProvider chatMemoryProvider = (ChatMemoryProvider) creationalContext
                                    .getInjectedReference(loadClass(chatMemoryProviderEntry.className()));
                            quarkusAiServices.chatMemoryProvider(chatMemoryProvider);
                        }
                        case SKIP -> {
                        }
                    }

                    // Chat memory flush strategy
                    DeclarativeAiServiceCreateInfo.ComponentEntry chatMemoryFlushStrategyEntry = info.chatMemoryFlushStrategy();
                    switch (chatMemoryFlushStrategyEntry.mode()) {
                        case EXPLICIT -> {
                            ChatMemoryFlushStrategy flushStrategy = (ChatMemoryFlushStrategy) creationalContext
                                    .getInjectedReference(loadClass(chatMemoryFlushStrategyEntry.className()));
                            quarkusAiServices.chatMemoryFlushStrategy(flushStrategy);
                        }
                        case AUTO_DISCOVER, SKIP -> {
                        }
                    }

                    // Retrieval augmentor
                    DeclarativeAiServiceCreateInfo.ComponentEntry retrievalAugmentorEntry = info.retrievalAugmentor();
                    switch (retrievalAugmentorEntry.mode()) {
                        case AUTO_DISCOVER -> {
                            Instance<RetrievalAugmentor> instance = creationalContext
                                    .getInjectedReference(RETRIEVAL_AUGMENTOR_TYPE_LITERAL);
                            if (instance.isResolvable()) {
                                quarkusAiServices.retrievalAugmentor(instance.get());
                            }
                        }
                        case EXPLICIT -> {
                            RetrievalAugmentor augmentor = (RetrievalAugmentor) creationalContext
                                    .getInjectedReference(loadClass(retrievalAugmentorEntry.className()));
                            quarkusAiServices.retrievalAugmentor(augmentor);
                        }
                        case SKIP -> {
                        }
                    }

                    // Moderation model
                    DeclarativeAiServiceCreateInfo.ComponentEntry moderationModelEntry = info.moderationModel();
                    if (moderationModelEntry.mode() != DeclarativeAiServiceCreateInfo.ComponentEntry.SKIP.mode()
                            && info.needsModerationModel()) {
                        if (NamedConfigUtil.isDefault(info.moderationModelName())) {
                            quarkusAiServices
                                    .moderationModel(creationalContext.getInjectedReference(ModerationModel.class));
                        } else {
                            quarkusAiServices.moderationModel(creationalContext.getInjectedReference(ModerationModel.class,
                                    ModelName.Literal.of(info.moderationModelName())));
                        }
                    }

                    // Image model
                    DeclarativeAiServiceCreateInfo.ComponentEntry imageModelEntry = info.imageModel();
                    if (imageModelEntry.mode() != DeclarativeAiServiceCreateInfo.ComponentEntry.SKIP.mode()
                            && info.needsImageModel()) {
                        if (NamedConfigUtil.isDefault(info.chatModelName())) {
                            quarkusAiServices
                                    .imageModel(creationalContext.getInjectedReference(ImageModel.class));
                        } else {
                            quarkusAiServices.imageModel(creationalContext.getInjectedReference(ImageModel.class,
                                    ModelName.Literal.of(info.chatModelName())));
                        }
                    }

                    if (info.chatMemorySeederClassName() != null) {
                        quarkusAiServices.chatMemorySeeder((ChatMemorySeeder) loadClass(
                                info.chatMemorySeederClassName())
                                .getConstructor().newInstance());
                    }

                    if (info.thinkingHandlerClassName() != null) {
                        quarkusAiServices.thinkingHandler((ThinkingHandler) loadClass(
                                info.thinkingHandlerClassName())
                                .getConstructor().newInstance());
                    }

                    // System message provider
                    DeclarativeAiServiceCreateInfo.ComponentEntry systemMessageProviderEntry = info.systemMessageProvider();
                    switch (systemMessageProviderEntry.mode()) {
                        case EXPLICIT -> {
                            Object provider = creationalContext
                                    .getInjectedReference(loadClass(systemMessageProviderEntry.className()));
                            if (provider instanceof SystemMessageProviderWithContext withContext) {
                                quarkusAiServices.systemMessageProvider(withContext);
                            } else if (provider instanceof SystemMessageProvider memoryIdProvider) {
                                quarkusAiServices.systemMessageProvider(memoryIdProvider);
                            }
                        }
                        case AUTO_DISCOVER, SKIP -> {
                        }
                    }

                    if (info.maxToolCallingRoundTrips() != null && info.maxToolCallingRoundTrips() > 0) {
                        quarkusAiServices.maxToolCallingRoundTrips(info.maxToolCallingRoundTrips());
                    }

                    if (info.maxToolCallsPerResponse() != null && info.maxToolCallsPerResponse() != 0) {
                        quarkusAiServices.maxToolCallsPerResponse(info.maxToolCallsPerResponse());
                    }

                    quarkusAiServices.allowContinuousForcedToolCalling(info.allowContinuousForcedToolCalling());

                    aiServiceContext.eventListenerRegistrar
                            .shouldThrowExceptionOnEventError(info.shouldThrowExceptionOnEventError());

                    return aiServiceContext;
                } catch (ClassNotFoundException e) {
                    throw new IllegalStateException(e);
                } catch (InvocationTargetException | NoSuchMethodException | IllegalAccessException
                        | InstantiationException e) {
                    throw new RuntimeException(e);
                }
            }

            private static Class<?> loadClass(String info) throws ClassNotFoundException {
                return Thread.currentThread().getContextClassLoader()
                        .loadClass(info);
            }
        };
    }
}
