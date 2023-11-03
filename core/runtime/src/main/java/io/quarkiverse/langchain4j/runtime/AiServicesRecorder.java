package io.quarkiverse.langchain4j.runtime;

import static io.quarkiverse.langchain4j.QuarkusAiServicesFactory.InstanceHolder.INSTANCE;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import jakarta.enterprise.util.TypeLiteral;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.retriever.Retriever;
import dev.langchain4j.service.AiServiceContext;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.runtime.aiservice.AiServiceClassCreateInfo;
import io.quarkiverse.langchain4j.runtime.aiservice.AiServiceMethodCreateInfo;
import io.quarkiverse.langchain4j.runtime.aiservice.DeclarativeAiServiceCreateInfo;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class AiServicesRecorder {

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

    @SuppressWarnings("unused") // used in generated code
    public static AiServiceMethodCreateInfo getAiServiceMethodCreateInfo(String className, String methodId) {
        AiServiceClassCreateInfo classCreateInfo = metadata.get(className);
        if (classCreateInfo == null) {
            throw new RuntimeException("Quarkus was not able to determine class '" + className
                    + "' as an AiService at build time. Consider annotating the clas with @CreatedAware");
        }
        AiServiceMethodCreateInfo methodCreateInfo = classCreateInfo.getMethodMap().get(methodId);
        if (methodCreateInfo == null) {
            throw new IllegalStateException("Unable to locate method metadata for descriptor '" + methodId
                    + "'. Please report this issue to the maintainers");
        }
        return methodCreateInfo;
    }

    public <T> Function<SyntheticCreationalContext<T>, T> createDeclarativeAiService(DeclarativeAiServiceCreateInfo info) {
        return new Function<SyntheticCreationalContext<T>, T>() {
            @SuppressWarnings("unchecked")
            @Override
            public T apply(SyntheticCreationalContext<T> creationalContext) {
                try {
                    Class<?> serviceClass = Thread.currentThread().getContextClassLoader()
                            .loadClass(info.getServiceClassName());

                    AiServiceContext aiServiceContext = new AiServiceContext(serviceClass);
                    var quarkusAiServices = INSTANCE.create(aiServiceContext);

                    if (info.getLanguageModelSupplierClassName() != null) {
                        Supplier<? extends ChatLanguageModel> supplier = (Supplier<? extends ChatLanguageModel>) Thread
                                .currentThread().getContextClassLoader().loadClass(info.getLanguageModelSupplierClassName())
                                .getConstructor().newInstance();
                        quarkusAiServices.chatLanguageModel(supplier.get());
                    } else {
                        quarkusAiServices.chatLanguageModel(creationalContext.getInjectedReference(ChatLanguageModel.class));
                    }

                    List<String> toolsClasses = info.getToolsClassNames();
                    if ((toolsClasses != null) && !toolsClasses.isEmpty()) {
                        List<Object> tools = new ArrayList<>(toolsClasses.size());
                        for (String toolClass : toolsClasses) {
                            Object tool = creationalContext.getInjectedReference(
                                    Thread.currentThread().getContextClassLoader().loadClass(toolClass));
                            tools.add(tool);
                        }
                        quarkusAiServices.tools(tools);
                    }

                    if (info.getChatMemoryProviderSupplierClassName() != null) {
                        if (RegisterAiService.BeanChatMemoryProviderSupplier.class.getName()
                                .equals(info.getChatMemoryProviderSupplierClassName())) {
                            quarkusAiServices.chatMemoryProvider(creationalContext.getInjectedReference(
                                    ChatMemoryProvider.class));
                        } else {
                            Supplier<? extends ChatMemoryProvider> supplier = (Supplier<? extends ChatMemoryProvider>) Thread
                                    .currentThread().getContextClassLoader()
                                    .loadClass(info.getChatMemoryProviderSupplierClassName())
                                    .getConstructor().newInstance();
                            quarkusAiServices.chatMemoryProvider(supplier.get());
                        }
                    }

                    if (info.getChatMemorySupplierClassName() != null) {
                        if (RegisterAiService.BeanChatMemorySupplier.class.getName()
                                .equals(info.getChatMemorySupplierClassName())) {
                            quarkusAiServices.chatMemory(creationalContext.getInjectedReference(ChatMemory.class));
                        } else {
                            Supplier<? extends ChatMemory> supplier = (Supplier<? extends ChatMemory>) Thread
                                    .currentThread().getContextClassLoader().loadClass(info.getChatMemorySupplierClassName())
                                    .getConstructor().newInstance();
                            quarkusAiServices.chatMemory(supplier.get());
                        }
                    }

                    if (info.getRetrieverSupplierClassName() != null) {
                        if (RegisterAiService.BeanRetrieverSupplier.class.getName()
                                .equals(info.getRetrieverSupplierClassName())) {
                            quarkusAiServices.retriever(creationalContext.getInjectedReference(new TypeLiteral<>() {
                            }));
                        } else {
                            @SuppressWarnings("rawtypes")
                            Supplier<? extends Retriever> supplier = (Supplier<? extends Retriever>) Thread
                                    .currentThread().getContextClassLoader().loadClass(info.getRetrieverSupplierClassName())
                                    .getConstructor().newInstance();
                            quarkusAiServices.retriever(supplier.get());
                        }
                    }

                    return (T) quarkusAiServices.build();
                } catch (ClassNotFoundException e) {
                    throw new IllegalStateException(e);
                } catch (InvocationTargetException | NoSuchMethodException | IllegalAccessException
                        | InstantiationException e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }
}
