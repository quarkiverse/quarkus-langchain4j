package io.quarkiverse.langchain4j.deployment;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.IndexView;
import org.objectweb.asm.ClassVisitor;

import com.knuddels.jtokkit.Encodings;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.input.structured.StructuredPrompt;
import dev.langchain4j.model.input.structured.StructuredPromptProcessor;
import dev.langchain4j.model.language.LanguageModel;
import dev.langchain4j.model.language.StreamingLanguageModel;
import dev.langchain4j.model.moderation.ModerationModel;
import io.quarkiverse.langchain4j.QuarkusPromptTemplateFactory;
import io.quarkiverse.langchain4j.runtime.Langchain4jModelsRecorder;
import io.quarkiverse.langchain4j.runtime.StructuredPromptsRecorder;
import io.quarkiverse.langchain4j.runtime.config.LangChain4jBuildConfig;
import io.quarkiverse.langchain4j.runtime.config.LangChain4jRuntimeConfig;
import io.quarkiverse.langchain4j.runtime.config.ModelProvider;
import io.quarkiverse.langchain4j.runtime.prompt.Mappable;
import io.quarkus.arc.deployment.BeanDiscoveryFinishedBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.processor.InjectionPointInfo;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.BytecodeTransformerBuildItem;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.IndexDependencyBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
import io.quarkus.gizmo.ClassTransformer;
import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo.ResultHandle;
import io.quarkus.qute.Expression;
import io.quarkus.qute.Template;
import io.quarkus.runtime.configuration.ConfigurationException;

class Langchain4jProcessor {

    private static final String FEATURE = "langchain4j";
    private static final DotName CHAT_MODEL = DotName.createSimple(ChatLanguageModel.class);
    private static final DotName STREAMING_CHAT_MODEL = DotName.createSimple(StreamingChatLanguageModel.class);
    private static final DotName LANGUAGE_MODEL = DotName.createSimple(LanguageModel.class);
    private static final DotName STREAMING_LANGUAGE_MODEL = DotName.createSimple(StreamingLanguageModel.class);
    private static final DotName EMBEDDING_MODEL = DotName.createSimple(EmbeddingModel.class);
    private static final DotName MODERATION_MODEL = DotName.createSimple(ModerationModel.class);
    private static final DotName STRUCTURED_PROMPT = DotName.createSimple(StructuredPrompt.class);
    private static final DotName STRUCTURED_PROMPT_PROCESSOR = DotName.createSimple(StructuredPromptProcessor.class);
    public static final DotName OBJECT = DotName.createSimple(Object.class.getName());

    public static final MethodDescriptor MAP_PUT = MethodDescriptor.ofMethod(Map.class, "put", Object.class, Object.class,
            Object.class);
    public static final MethodDescriptor MAP_PUT_ALL = MethodDescriptor.ofMethod(Map.class, "putAll", void.class, Map.class);

    public static final MethodDescriptor MAPPABLE_OBTAIN = MethodDescriptor.ofMethod(Mappable.class, "obtainFieldValuesMap",
            Map.class);

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void indexDependencies(BuildProducer<IndexDependencyBuildItem> producer) {
        producer.produce(new IndexDependencyBuildItem("dev.ai4j", "openai4j"));
        producer.produce(new IndexDependencyBuildItem("dev.langchain4j", "langchain4j-core"));
    }

    @BuildStep
    void nativeImageSupport(BuildProducer<NativeImageResourceBuildItem> resourcesProducer) {
        registerJtokkitResources(resourcesProducer);
    }

    private void registerJtokkitResources(BuildProducer<NativeImageResourceBuildItem> resourcesProducer) {
        List<String> resources = new ArrayList<>();
        try (JarFile jarFile = new JarFile(determineJarLocation(Encodings.class).toFile())) {
            Enumeration<JarEntry> e = jarFile.entries();
            while (e.hasMoreElements()) {
                String name = e.nextElement().getName();
                if (name.endsWith(".tiktoken")) {
                    resources.add(name);
                }

            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        resourcesProducer.produce(new NativeImageResourceBuildItem(resources));
    }

    private static Path determineJarLocation(Class<?> classFromJar) {
        URL url = classFromJar.getProtectionDomain().getCodeSource().getLocation();
        if (!url.getProtocol().equals("file")) {
            throw new IllegalStateException("Unable to find which jar class " + classFromJar + " belongs to");
        }
        try {
            return Paths.get(url.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void generateBeans(LangChain4jBuildConfig buildConfig, LangChain4jRuntimeConfig runtimeConfig,
            BeanDiscoveryFinishedBuildItem beanDiscoveryFinished,
            BuildProducer<SyntheticBeanBuildItem> beanProducer,
            Langchain4jModelsRecorder recorder) {

        boolean chatModelBeanRequested = false;
        boolean streamingChatModelBeanRequested = false;
        boolean languageModelBeanRequested = false;
        boolean streamingLanguageModelBeanRequested = false;
        boolean embeddingModelBeanRequested = false;
        boolean moderationModelBeanRequested = false;
        for (InjectionPointInfo ip : beanDiscoveryFinished.getInjectionPoints()) {
            DotName requiredName = ip.getRequiredType().name();
            if (CHAT_MODEL.equals(requiredName)) {
                chatModelBeanRequested = true;
            } else if (STREAMING_CHAT_MODEL.equals(requiredName)) {
                streamingChatModelBeanRequested = true;
            } else if (LANGUAGE_MODEL.equals(requiredName)) {
                languageModelBeanRequested = true;
            } else if (STREAMING_LANGUAGE_MODEL.equals(requiredName)) {
                streamingLanguageModelBeanRequested = true;
            } else if (EMBEDDING_MODEL.equals(requiredName)) {
                embeddingModelBeanRequested = true;
            } else if (MODERATION_MODEL.equals(requiredName)) {
                moderationModelBeanRequested = true;
            }
        }

        if (chatModelBeanRequested) {
            Optional<ModelProvider> provider = buildConfig.chatModel().provider();
            if (provider.isEmpty()) {
                throw new ConfigurationException(configErrorMessage(CHAT_MODEL, "chat-model"));
            }

            beanProducer.produce(SyntheticBeanBuildItem
                    .configure(CHAT_MODEL)
                    .setRuntimeInit()
                    .scope(ApplicationScoped.class)
                    .runtimeValue(recorder.chatModel(provider.get(), runtimeConfig))
                    .done());
        }
        if (streamingChatModelBeanRequested) {
            Optional<ModelProvider> provider = buildConfig.chatModel().provider();
            if (provider.isEmpty()) {
                throw new ConfigurationException(configErrorMessage(CHAT_MODEL, "chat-model"));
            }

            beanProducer.produce(SyntheticBeanBuildItem
                    .configure(STREAMING_CHAT_MODEL)
                    .setRuntimeInit()
                    .scope(ApplicationScoped.class)
                    .runtimeValue(recorder.streamingChatModel(provider.get(), runtimeConfig))
                    .done());
        }
        if (languageModelBeanRequested) {
            Optional<ModelProvider> provider = buildConfig.languageModel().provider();
            if (provider.isEmpty()) {
                throw new ConfigurationException(configErrorMessage(LANGUAGE_MODEL, "language-model"));
            }

            beanProducer.produce(SyntheticBeanBuildItem
                    .configure(LANGUAGE_MODEL)
                    .setRuntimeInit()
                    .scope(ApplicationScoped.class)
                    .runtimeValue(recorder.languageModel(provider.get(), runtimeConfig))
                    .done());
        }
        if (streamingLanguageModelBeanRequested) {
            Optional<ModelProvider> provider = buildConfig.languageModel().provider();
            if (provider.isEmpty()) {
                throw new ConfigurationException(configErrorMessage(LANGUAGE_MODEL, "language-model"));
            }

            beanProducer.produce(SyntheticBeanBuildItem
                    .configure(STREAMING_LANGUAGE_MODEL)
                    .setRuntimeInit()
                    .scope(ApplicationScoped.class)
                    .runtimeValue(recorder.streamingLanguageModel(provider.get(), runtimeConfig))
                    .done());
        }
        if (embeddingModelBeanRequested) {
            Optional<ModelProvider> provider = buildConfig.embeddingModel().provider();
            if (provider.isEmpty()) {
                throw new ConfigurationException(configErrorMessage(EMBEDDING_MODEL, "embedding-model"));
            }

            beanProducer.produce(SyntheticBeanBuildItem
                    .configure(EMBEDDING_MODEL)
                    .setRuntimeInit()
                    .scope(ApplicationScoped.class)
                    .runtimeValue(recorder.embeddingModel(provider.get(), runtimeConfig))
                    .done());
        }
        if (moderationModelBeanRequested) {
            Optional<ModelProvider> provider = buildConfig.moderationModel().provider();
            if (provider.isEmpty()) {
                throw new ConfigurationException(configErrorMessage(MODERATION_MODEL, "moderation-model"));
            }
            if (ModelProvider.OPEN_AI != provider.get()) {
                throw new ConfigurationException("Currently 'openai' is the only supported provider of the moderation model");
            }
            beanProducer.produce(SyntheticBeanBuildItem
                    .configure(MODERATION_MODEL)
                    .setRuntimeInit()
                    .scope(ApplicationScoped.class)
                    .runtimeValue(recorder.moderationModel(provider.get(), runtimeConfig))
                    .done());
        }
    }

    private static String configErrorMessage(DotName beanType, String configNamespace) {
        return String.format(
                "When a bean of type '%s' is being used the 'quarkus.langchain4j.%s.provider' property must be set", beanType,
                configNamespace);
    }

    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    public void structuredPromptSupport(StructuredPromptsRecorder recorder,
            CombinedIndexBuildItem combinedIndexBuildItem,
            BuildProducer<BytecodeTransformerBuildItem> transformerProducer) {
        IndexView index = combinedIndexBuildItem.getIndex();

        Collection<AnnotationInstance> instances = index.getAnnotations(STRUCTURED_PROMPT);
        for (AnnotationInstance instance : instances) {
            AnnotationTarget target = instance.target();
            if (target.kind() != AnnotationTarget.Kind.CLASS) {
                continue; // should never happen
            }
            String[] parts = instance.value().asStringArray();
            AnnotationValue delimiterValue = instance.value("delimiter");
            String delimiter = delimiterValue != null ? delimiterValue.asString() : "\n";
            String promptTemplateString = String.join(delimiter, parts);
            ClassInfo annotatedClass = target.asClass();
            boolean hasNestedParams = isMappable(promptTemplateString);
            if (!hasNestedParams) {
                ClassInfo current = annotatedClass;
                while (true) {
                    DotName superName = current.superName();
                    ClassInfo superClassInfo = OBJECT.equals(superName) ? null : index.getClassByName(superName);
                    transformerProducer.produce(new BytecodeTransformerBuildItem(current.name().toString(),
                            new StructuredPromptAnnotatedTransformer(current, superClassInfo != null,
                                    superName.toString())));
                    if (superClassInfo == null) {
                        break;
                    }
                    current = superClassInfo;
                }

            }
            recorder.add(annotatedClass.name().toString(), promptTemplateString);
        }
    }

    /**
     * We can obtain a map of the class values if the template does not try to access values in any nested level
     */
    private static boolean isMappable(String promptTemplateString) {
        Template template = QuarkusPromptTemplateFactory.ENGINE.parse(promptTemplateString);
        boolean hasNestedParams = false;
        List<Expression> expressions = template.getExpressions();
        for (Expression expression : expressions) {
            if (expression.getParts().size() > 1) {
                hasNestedParams = true;
            }
        }
        return hasNestedParams;
    }

    /**
     * Simple class transformer that adds the {@link Mappable} interface to the class and implement the method
     * by simply reading all properties of the class itself
     */
    private static class StructuredPromptAnnotatedTransformer implements BiFunction<String, ClassVisitor, ClassVisitor> {

        private final ClassInfo annotatedClass;
        private final boolean hasSuperMappable;
        private final String superClassName;

        private StructuredPromptAnnotatedTransformer(ClassInfo annotatedClass, boolean hasSuperMappable,
                String superClassName) {
            this.annotatedClass = annotatedClass;
            this.hasSuperMappable = hasSuperMappable;
            this.superClassName = superClassName;
        }

        @Override
        public ClassVisitor apply(String s, ClassVisitor classVisitor) {
            ClassTransformer transformer = new ClassTransformer(annotatedClass.name().toString());
            transformer.addInterface(Mappable.class);

            MethodCreator mc = transformer.addMethod("obtainFieldValuesMap", Map.class);
            ResultHandle mapHandle = mc.newInstance(MethodDescriptor.ofConstructor(HashMap.class));
            for (FieldInfo field : annotatedClass.fields()) {
                short modifiers = field.flags();
                if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers)) {
                    continue;
                }
                String name = field.name();
                ResultHandle fieldValue = mc.readInstanceField(field, mc.getThis());
                mc.invokeInterfaceMethod(MAP_PUT, mapHandle, mc.load(name), fieldValue);

            }
            if (hasSuperMappable) {
                ResultHandle mapFromSuper = mc
                        .invokeSpecialMethod(MethodDescriptor.ofMethod(superClassName, "obtainFieldValuesMap",
                                Map.class), mc.getThis());
                mc.invokeInterfaceMethod(MAP_PUT_ALL, mapFromSuper, mapHandle);
                mc.returnValue(mapFromSuper);
            } else {
                mc.returnValue(mapHandle);
            }

            return transformer.applyTo(classVisitor);
        }
    }
}
