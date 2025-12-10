package io.quarkiverse.langchain4j.runtime;

import static io.quarkiverse.langchain4j.QuarkusAiServicesFactory.InstanceHolder.INSTANCE;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.enterprise.util.TypeLiteral;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.service.tool.ToolProvider;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.observability.AiServiceEvents;
import io.quarkiverse.langchain4j.runtime.aiservice.AiServiceClassCreateInfo;
import io.quarkiverse.langchain4j.runtime.aiservice.AiServiceMethodCreateInfo;
import io.quarkiverse.langchain4j.runtime.aiservice.ChatMemorySeeder;
import io.quarkiverse.langchain4j.runtime.aiservice.DeclarativeAiServiceCreateInfo;
import io.quarkiverse.langchain4j.runtime.aiservice.QuarkusAiServiceContext;
import io.quarkus.arc.Arc;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class AiServicesRecorder {
    private static final TypeLiteral<Instance<RetrievalAugmentor>> RETRIEVAL_AUGMENTOR_TYPE_LITERAL = new TypeLiteral<>() {
    };

    private static final TypeLiteral<Instance<ToolProvider>> TOOL_PROVIDER_TYPE_LITERAL = new TypeLiteral<>() {
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
                    Class<?> serviceClass = Thread.currentThread().getContextClassLoader()
                            .loadClass(info.serviceClassName());

                    QuarkusAiServiceContext aiServiceContext = new QuarkusAiServiceContext(serviceClass);
                    // we don't really care about QuarkusAiServices here, all we care about is that it
                    // properly populates QuarkusAiServiceContext which is what we are trying to construct
                    var quarkusAiServices = INSTANCE.create(aiServiceContext);

                    // Populate the AI service listeners
                    Arrays.stream(AiServiceEvents.values())
                            .map(event -> event.createListener(serviceClass))
                            .forEach(quarkusAiServices::registerListener);

                    if (info.languageModelSupplierClassName() != null
                            || info.streamingChatLanguageModelSupplierClassName() != null) {
                        if (info.languageModelSupplierClassName() != null) {
                            Supplier<? extends ChatModel> supplier = createSupplier(
                                    info.languageModelSupplierClassName());
                            quarkusAiServices.chatModel(supplier.get());
                        }
                        if (info.streamingChatLanguageModelSupplierClassName() != null) {
                            Supplier<? extends StreamingChatModel> supplier = createSupplier(
                                    info.streamingChatLanguageModelSupplierClassName());
                            quarkusAiServices.streamingChatModel(supplier.get());
                        }
                    } else {
                        if (NamedConfigUtil.isDefault(info.chatModelName())) {
                            quarkusAiServices
                                    .chatModel(creationalContext.getInjectedReference(ChatModel.class));
                            if (info.needsStreamingChatModel()) {
                                quarkusAiServices
                                        .streamingChatModel(
                                                creationalContext.getInjectedReference(StreamingChatModel.class));
                            }

                        } else {

                            quarkusAiServices.chatModel(creationalContext.getInjectedReference(ChatModel.class,
                                    ModelName.Literal.of(info.chatModelName())));

                            if (info.needsStreamingChatModel()) {
                                quarkusAiServices.streamingChatModel(
                                        creationalContext.getInjectedReference(StreamingChatModel.class,
                                                ModelName.Literal.of(info.chatModelName())));
                            }
                        }
                    }

                    Map<String, AnnotationLiteral<?>> toolsClasses = info.toolsClassInfo();
                    boolean hasExplicitTools = (toolsClasses != null) && !toolsClasses.isEmpty();
                    if (hasExplicitTools) {
                        List<Object> tools = new ArrayList<>(toolsClasses.size());
                        for (var entry : toolsClasses.entrySet()) {
                            AnnotationLiteral<?> qualifier = entry.getValue();
                            Object tool;
                            if (qualifier != null) {
                                tool = creationalContext.getInjectedReference(
                                        Thread.currentThread().getContextClassLoader().loadClass(entry.getKey()),
                                        qualifier);
                            } else {
                                tool = creationalContext.getInjectedReference(
                                        Thread.currentThread().getContextClassLoader().loadClass(entry.getKey()));
                            }
                            if (tool == null) {
                                throw new IllegalStateException("Unknown tool: " + entry.getKey());
                            }
                            tools.add(tool);
                        }
                        quarkusAiServices.tools(tools);
                    }

                    if (info.toolHallucinationStrategyClassName() != null) {
                        Object toolHallucinationStrategy = creationalContext.getInjectedReference(
                                Thread.currentThread().getContextClassLoader()
                                        .loadClass(info.toolHallucinationStrategyClassName()));
                        if (toolHallucinationStrategy == null) {
                            throw new IllegalStateException(
                                    "Unknown tool hallucination strategy: " + info.toolHallucinationStrategyClassName());
                        }
                        quarkusAiServices.toolHallucinationStrategy(toolHallucinationStrategy);
                    }

                    // if no explicit tools are provided, check if we should use a tool provider
                    if (info.toolProviderSupplier() != null) {
                        if (!RegisterAiService.BeanIfExistsToolProviderSupplier.class.getName()
                                .equals(info.toolProviderSupplier())) {
                            // specific provider
                            Class<?> toolProviderClass = Thread.currentThread().getContextClassLoader()
                                    .loadClass(info.toolProviderSupplier());
                            Supplier<? extends ToolProvider> toolProvider = (Supplier<? extends ToolProvider>) creationalContext
                                    .getInjectedReference(toolProviderClass);
                            quarkusAiServices.toolProvider(toolProvider.get());
                        } else {
                            // if-exists provider
                            Instance<ToolProvider> instance = creationalContext
                                    .getInjectedReference(TOOL_PROVIDER_TYPE_LITERAL);
                            // if the service has explicit tools and a BeanIfExistsToolProviderSupplier,
                            // just give priority to the explicit tools, don't throw an error
                            if (instance.isResolvable() && !hasExplicitTools) {
                                quarkusAiServices.toolProvider(instance.get());
                            }
                        }
                    }

                    if (info.chatMemoryProviderSupplierClassName() != null) {
                        if (RegisterAiService.BeanChatMemoryProviderSupplier.class.getName()
                                .equals(info.chatMemoryProviderSupplierClassName())) {
                            quarkusAiServices.chatMemoryProvider(creationalContext.getInjectedReference(
                                    ChatMemoryProvider.class));
                        } else {
                            Supplier<? extends ChatMemoryProvider> supplier = (Supplier<? extends ChatMemoryProvider>) Thread
                                    .currentThread().getContextClassLoader()
                                    .loadClass(info.chatMemoryProviderSupplierClassName())
                                    .getConstructor().newInstance();
                            quarkusAiServices.chatMemoryProvider(supplier.get());
                        }
                    }

                    if (info.retrievalAugmentorSupplierClassName() != null) {
                        if (RegisterAiService.BeanIfExistsRetrievalAugmentorSupplier.class.getName()
                                .equals(info.retrievalAugmentorSupplierClassName())) {
                            Instance<RetrievalAugmentor> instance = creationalContext
                                    .getInjectedReference(RETRIEVAL_AUGMENTOR_TYPE_LITERAL);
                            if (instance.isResolvable()) {
                                quarkusAiServices.retrievalAugmentor(instance.get());
                            }
                        } else {
                            try {
                                Supplier<RetrievalAugmentor> instance = (Supplier<RetrievalAugmentor>) creationalContext
                                        .getInjectedReference(Thread.currentThread().getContextClassLoader()
                                                .loadClass(info.retrievalAugmentorSupplierClassName()));
                                quarkusAiServices.retrievalAugmentor(instance.get());
                            } catch (IllegalArgumentException e) {
                                // the provided Supplier is not a CDI bean, build it manually
                                Supplier<? extends RetrievalAugmentor> supplier = (Supplier<? extends RetrievalAugmentor>) Thread
                                        .currentThread().getContextClassLoader()
                                        .loadClass(info.retrievalAugmentorSupplierClassName())
                                        .getConstructor().newInstance();
                                quarkusAiServices.retrievalAugmentor(supplier.get());
                            }
                        }
                    }

                    if (info.moderationModelSupplierClassName() != null && info.needsModerationModel()) {
                        if (RegisterAiService.BeanIfExistsModerationModelSupplier.class.getName()
                                .equals(info.moderationModelSupplierClassName())) {

                            if (NamedConfigUtil.isDefault(info.moderationModelName())) {
                                quarkusAiServices
                                        .moderationModel(creationalContext.getInjectedReference(ModerationModel.class));

                            } else {
                                quarkusAiServices.moderationModel(creationalContext.getInjectedReference(ModerationModel.class,
                                        ModelName.Literal.of(info.moderationModelName())));
                            }
                        } else {
                            Supplier<? extends ModerationModel> supplier = (Supplier<? extends ModerationModel>) Thread
                                    .currentThread().getContextClassLoader()
                                    .loadClass(info.moderationModelSupplierClassName())
                                    .getConstructor().newInstance();
                            quarkusAiServices.moderationModel(supplier.get());
                        }
                    }

                    if (info.imageModelSupplierClassName() != null && info.needsImageModel()) {
                        if (RegisterAiService.BeanIfExistsImageModelSupplier.class.getName()
                                .equals(info.imageModelSupplierClassName())) {
                            if (NamedConfigUtil.isDefault(info.chatModelName())) {
                                quarkusAiServices
                                        .imageModel(creationalContext.getInjectedReference(ImageModel.class));

                            } else {
                                quarkusAiServices.imageModel(creationalContext.getInjectedReference(ImageModel.class,
                                        ModelName.Literal.of(info.chatModelName())));
                            }

                        } else {
                            Supplier<? extends ImageModel> supplier = (Supplier<? extends ImageModel>) Thread
                                    .currentThread().getContextClassLoader()
                                    .loadClass(info.imageModelSupplierClassName())
                                    .getConstructor().newInstance();
                            quarkusAiServices.imageModel(supplier.get());
                        }
                    }

                    if (info.chatMemorySeederClassName() != null) {
                        quarkusAiServices.chatMemorySeeder((ChatMemorySeeder) Thread
                                .currentThread().getContextClassLoader()
                                .loadClass(info.chatMemorySeederClassName())
                                .getConstructor().newInstance());
                    }
                    if (info.maxSequentialToolInvocations() != null && info.maxSequentialToolInvocations() > 0) {
                        quarkusAiServices.maxSequentialToolInvocations(info.maxSequentialToolInvocations());
                    }

                    if (info.maxSequentialToolInvocations() != null && info.maxSequentialToolInvocations() > 0) {
                        quarkusAiServices.maxSequentialToolInvocations(info.maxSequentialToolInvocations());
                    }

                    quarkusAiServices.allowContinuousForcedToolCalling(info.allowContinuousForcedToolCalling());

                    return aiServiceContext;
                } catch (ClassNotFoundException e) {
                    throw new IllegalStateException(e);
                } catch (InvocationTargetException | NoSuchMethodException | IllegalAccessException
                        | InstantiationException e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    private static <T> Supplier<T> createSupplier(String className) throws InstantiationException, IllegalAccessException,
            InvocationTargetException, NoSuchMethodException, ClassNotFoundException {
        return (Supplier<T>) Thread
                .currentThread().getContextClassLoader().loadClass(className)
                .getConstructor().newInstance();
    }
}
