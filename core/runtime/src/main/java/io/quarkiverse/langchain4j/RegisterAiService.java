package io.quarkiverse.langchain4j;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.retriever.Retriever;
import dev.langchain4j.service.AiServices;

/**
 * Used to create Langchain4j's {@link AiServices} in a declarative manner that the application can then use simply by
 * using the class as a CDI bean.
 * Under the hood Langchain4j's {@link AiServices#builder(Class)} is called
 * while also providing the builder with the proper {@link ChatLanguageModel} bean (mandatory), {@code tools} bean (optional),
 * {@link ChatMemoryProvider} and {@link Retriever} beans (which by default are configured if such beans exist).
 * <p>
 * NOTE: The resulting CDI bean is {@link ApplicationScoped}.
 * <p>
 * NOTE: When the application also contains the {@code quarkus-micrometer} extension, metrics are automatically generated
 * for the method invocations.
 */
@Retention(RUNTIME)
@Target(ElementType.TYPE)
public @interface RegisterAiService {

    /**
     * Configures the way to obtain the {@link ChatLanguageModel} to use.
     * If not configured, the default CDI bean implementing the model is looked up.
     * Such a bean provided automatically by extensions such as {@code quarkus-langchain4j-openai},
     * {@code quarkus-langchain4j-azure-openai} or
     * {@code quarkus-langchain4j-hugging-face}
     */
    Class<? extends Supplier<ChatLanguageModel>> chatLanguageModelSupplier() default BeanChatLanguageModelSupplier.class;

    /**
     * Tool classes to use. All tools are expected to be CDI beans.
     * <p>
     * NOTE: when this is used, {@code chatMemoryProviderSupplier} must NOT be set to {@link NoChatMemoryProviderSupplier}.
     */
    Class<?>[] tools() default {};

    /**
     * Configures the way to obtain the {@link ChatMemoryProvider} to use.
     * By default, Quarkus will look for a CDI bean that implements {@link ChatMemoryProvider}, but will fall back to not using
     * any memory if no such bean exists.
     * If an arbitrary {@link ChatMemoryProvider} instance is needed, a custom implementation of
     * {@link Supplier<ChatMemoryProvider>} needs to be provided.
     * <p>
     * If the memory provider to use is exposed as a CDI bean exposing the type {@link ChatMemoryProvider}, then
     * set the value to {@link RegisterAiService.BeanChatMemoryProviderSupplier}
     */
    Class<? extends Supplier<ChatMemoryProvider>> chatMemoryProviderSupplier() default BeanIfExistsChatMemoryProviderSupplier.class;

    /**
     * Configures the way to obtain the {@link Retriever} to use (when using RAG).
     * By default, no chat memory is used.
     * If a CDI bean of type {@link ChatMemory} is needed, the value should be {@link BeanRetrieverSupplier}.
     * If an arbitrary {@link ChatMemory} instance is needed, a custom implementation of {@link Supplier<ChatMemory>}
     * needs to be provided.
     */
    Class<? extends Supplier<Retriever<TextSegment>>> retrieverSupplier() default NoRetrieverSupplier.class;

    /**
     * Marker that is used to tell Quarkus to use the {@link ChatLanguageModel} that has been configured as a CDI bean by
     * any of the extensions providing such capability (such as {@code quarkus-langchain4j-openai} and
     * {@code quarkus-langchain4j-hugging-face}).
     */
    final class BeanChatLanguageModelSupplier implements Supplier<ChatLanguageModel> {

        @Override
        public ChatLanguageModel get() {
            throw new UnsupportedOperationException("should never be called");
        }
    }

    /**
     * Marker that is used to tell Quarkus to use the retriever that the user has configured as a CDI bean. If the bean does
     * not exist, Quarkus will fail at build time.
     */
    final class BeanChatMemoryProviderSupplier implements Supplier<ChatMemoryProvider> {

        @Override
        public ChatMemoryProvider get() {
            throw new UnsupportedOperationException("should never be called");
        }
    }

    /**
     * Marker that is used to tell Quarkus to use the {@link ChatMemoryProvider} that the user has configured as a CDI bean.
     * If no such bean exists, then no memory will be used.
     */
    final class BeanIfExistsChatMemoryProviderSupplier implements Supplier<ChatMemoryProvider> {

        @Override
        public ChatMemoryProvider get() {
            throw new UnsupportedOperationException("should never be called");
        }
    }

    /**
     * Marker class to indicate that no chat memory should be used
     */
    final class NoChatMemoryProviderSupplier implements Supplier<ChatMemoryProvider> {

        @Override
        public ChatMemoryProvider get() {
            throw new UnsupportedOperationException("should never be called");
        }
    }

    /**
     * Marker that is used to tell Quarkus to use the retriever that the user has configured as a CDI bean
     */
    final class BeanRetrieverSupplier implements Supplier<Retriever<TextSegment>> {

        @Override
        public Retriever<TextSegment> get() {
            throw new UnsupportedOperationException("should never be called");
        }
    }

    /**
     * Marker that is used to tell Quarkus to use the {@link Retriever} that the user has configured as a CDI bean.
     * If no such bean exists, then no retriever will be used.
     */
    final class BeanIfExistsRetrieverSupplier implements Supplier<Retriever<TextSegment>> {

        @Override
        public Retriever<TextSegment> get() {
            throw new UnsupportedOperationException("should never be called");
        }
    }

    /**
     * Marker class to indicate that no retriever should be used
     */
    final class NoRetrieverSupplier implements Supplier<Retriever<TextSegment>> {

        @Override
        public Retriever<TextSegment> get() {
            throw new UnsupportedOperationException("should never be called");
        }
    }
}
