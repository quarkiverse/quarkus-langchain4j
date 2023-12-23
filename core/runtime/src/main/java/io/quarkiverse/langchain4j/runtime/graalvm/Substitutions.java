package io.quarkiverse.langchain4j.runtime.graalvm;

import java.lang.reflect.Field;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

import dev.langchain4j.data.message.ChatMessageJsonCodec;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.internal.Json;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.input.structured.StructuredPromptProcessor;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.spi.prompt.PromptTemplateFactory;
import dev.langchain4j.spi.prompt.structured.StructuredPromptFactory;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStoreJsonCodec;
import io.quarkiverse.langchain4j.QuarkusAiServicesFactory;
import io.quarkiverse.langchain4j.QuarkusChatMessageJsonCodecFactory;
import io.quarkiverse.langchain4j.QuarkusInMemoryEmbeddingJsonCodecFactory;
import io.quarkiverse.langchain4j.QuarkusJsonCodecFactory;
import io.quarkiverse.langchain4j.QuarkusPromptTemplateFactory;
import io.quarkiverse.langchain4j.QuarkusStructuredPromptFactory;
import io.quarkiverse.langchain4j.runtime.aiservice.QuarkusAiServiceContext;
import opennlp.tools.util.ext.ExtensionNotLoadedException;

public class Substitutions {

    @TargetClass(PromptTemplate.class)
    static final class Target_PromptTemplate {

        @Substitute
        private static PromptTemplateFactory factory() {
            return new QuarkusPromptTemplateFactory();
        }
    }

    @TargetClass(StructuredPromptProcessor.class)
    static final class Target_StructuredPromptProcessor {

        @Substitute
        private static StructuredPromptFactory factory() {
            return new QuarkusStructuredPromptFactory();
        }
    }

    @TargetClass(AiServices.class)
    static final class Target_AiServices {

        @Substitute
        public static <T> AiServices<T> builder(Class<T> aiService) {
            return new QuarkusAiServicesFactory.QuarkusAiServices<>(new QuarkusAiServiceContext(aiService));
        }
    }

    @TargetClass(Json.class)
    static final class Target_Json {

        @Substitute
        private static Json.JsonCodec loadCodec() {
            return new QuarkusJsonCodecFactory().create();
        }
    }

    @TargetClass(ChatMessageSerializer.class)
    static final class Target_ChatMessageSerializer {

        @Substitute
        private static ChatMessageJsonCodec loadCodec() {
            return new QuarkusChatMessageJsonCodecFactory().create();
        }
    }

    @TargetClass(InMemoryEmbeddingStore.class)
    static final class Target_InMemoryEmbeddingStore {

        @Substitute
        private static InMemoryEmbeddingStoreJsonCodec loadCodec() {
            return new QuarkusInMemoryEmbeddingJsonCodecFactory().create();
        }
    }

    @TargetClass(opennlp.tools.util.ext.ExtensionLoader.class)
    static final class Target_ExtensionLoader {

        /**
         * This is needed because otherwise OSGi comes into play and breaks everything...
         */
        @Substitute
        public static <T> T instantiateExtension(Class<T> clazz, String extensionClassName) {

            // First try to load extension and instantiate extension from class path
            try {
                Class<?> extClazz = Class.forName(extensionClassName);

                if (clazz.isAssignableFrom(extClazz)) {

                    try {
                        return (T) extClazz.newInstance();
                    } catch (InstantiationException e) {
                        throw new ExtensionNotLoadedException(e);
                    } catch (IllegalAccessException e) {
                        // constructor is private. Try to load using INSTANCE
                        Field instanceField;
                        try {
                            instanceField = extClazz.getDeclaredField("INSTANCE");
                        } catch (NoSuchFieldException | SecurityException e1) {
                            throw new ExtensionNotLoadedException(e1);
                        }
                        if (instanceField != null) {
                            try {
                                return (T) instanceField.get(null);
                            } catch (IllegalArgumentException | IllegalAccessException e1) {
                                throw new ExtensionNotLoadedException(e1);
                            }
                        }
                        throw new ExtensionNotLoadedException(e);
                    }
                } else {
                    throw new ExtensionNotLoadedException("Extension class '" + extClazz.getName() +
                            "' needs to have type: " + clazz.getName());
                }
            } catch (ClassNotFoundException e) {
                // Class is not on classpath
            }

            throw new ExtensionNotLoadedException("Unable to find implementation for " +
                    clazz.getName() + ", the class or service " + extensionClassName +
                    " could not be located!");
        }
    }
}
