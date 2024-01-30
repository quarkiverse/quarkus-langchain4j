package io.quarkiverse.langchain4j;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.List;
import java.util.function.Supplier;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.retriever.Retriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import io.quarkiverse.langchain4j.audit.AuditService;

/**
 * Used to create Langchain4j's {@link AiServices} in a declarative manner that the application can then use simply by
 * using the class as a CDI bean.
 * Under the hood Langchain4j's {@link AiServices#builder(Class)} is called
 * while also providing the builder with the proper {@link ChatLanguageModel} bean (mandatory), {@code tools} bean (optional),
 * {@link ChatMemoryProvider} and {@link Retriever} beans (which by default are configured if such beans exist).
 * <p>
 * NOTE: The resulting CDI bean is {@link jakarta.enterprise.context.RequestScoped} by default. If you need to change the scope,
 * simply annotate the class with a CDI scope.
 * CAUTION: When using anything other than the request scope, you need to be very careful with the chat memory implementation.
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
     * When {@code chatLanguageModelSupplier} is set to {@code BeanChatLanguageModelSupplier.class} (which is the default)
     * this allows the selection of the {@link ChatLanguageModel} CDI bean to use.
     * <p>
     * If not set, the default model (i.e. the one configured without setting the model name) is used.
     * An example of the default model configuration is the following:
     * {@code quarkus.langchain4j.openai.chat-model.model-name=gpt-4-turbo-preview}
     *
     * If set, it uses the model configured by name. For example if this is set to {@code somename}
     * an example configuration value for that named model could be:
     * {@code quarkus.langchain4j.somename.openai.chat-model.model-name=gpt-4-turbo-preview}
     */
    String modelName() default "<default>";

    /**
     * Tool classes to use. All tools are expected to be CDI beans.
     */
    Class<?>[] tools() default {};

    /**
     * Configures the way to obtain the {@link ChatMemoryProvider}.
     * <p>
     * Be default, Quarkus configures a {@link ChatMemoryProvider} bean that uses a {@link InMemoryChatMemoryStore} bean
     * as the backing store. The default type for the actual {@link ChatMemory} is {@link MessageWindowChatMemory}
     * and it is configured with the value of the {@code quarkus.langchain4j.chat-memory.memory-window.max-messages}
     * configuration property (which default to 10) as a way of limiting the number of messages in each chat.
     * <p>
     * If the application provides its own {@link ChatMemoryProvider} bean, that takes precedence over what Quarkus provides as
     * the default.
     * <p>
     * If the application provides an implementation of {@link ChatMemoryStore}, then that is used instead of the default
     * {@link InMemoryChatMemoryStore}.
     * <p>
     * In the most advances case, an arbitrary {@link ChatMemoryProvider} can be used by having a custom
     * {@code Supplier<ChatMemoryProvider>} configured in this property.
     * {@link Supplier<ChatMemoryProvider>} needs to be provided.
     * <p>
     */
    Class<? extends Supplier<ChatMemoryProvider>> chatMemoryProviderSupplier() default BeanChatMemoryProviderSupplier.class;

    /**
     * Configures the way to obtain the {@link Retriever} to use (when using RAG). All tools are expected to be CDI beans
     * By default, no supplier is used.
     */
    Class<? extends Retriever<TextSegment>> retriever() default NoRetriever.class;

    /**
     * Configures the way to obtain the {@link AuditService} to use.
     * By default, Quarkus will look for a CDI bean that implements {@link AuditService}, but will fall back to not using
     * any memory if no such bean exists.
     * If an arbitrary {@link AuditService} instance is needed, a custom implementation of
     * {@link Supplier<AuditService>} needs to be provided.
     */
    Class<? extends Supplier<AuditService>> auditServiceSupplier() default BeanIfExistsAuditServiceSupplier.class;

    /**
     * Configures the way to obtain the {@link ModerationModel} to use.
     * By default, no moderation model is used.
     * If a CDI bean of type {@link ChatMemory} is needed, the value should be {@link BeanRetrieverSupplier}.
     * If an arbitrary {@link ChatMemory} instance is needed, a custom implementation of {@link Supplier<ChatMemory>}
     * needs to be provided.
     */
    Class<? extends Supplier<ModerationModel>> moderationModelSupplier() default NoModerationModelSupplier.class;

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
     * Marker that is used to tell Quarkus to use the retriever that the user has configured as a CDI bean.
     * Be default, Quarkus configures an {@link ChatMemoryProvider} by using an {@link InMemoryChatMemoryStore}
     * as the backing store while using {@link MessageWindowChatMemory} with the value of
     * configuration property {@code quarkus.langchain4j.chat-memory.memory-window.max-messages} (which default to 10)
     * as a way of limiting the number of messages in each chat.
     */
    final class BeanChatMemoryProviderSupplier implements Supplier<ChatMemoryProvider> {

        @Override
        public ChatMemoryProvider get() {
            throw new UnsupportedOperationException("should never be called");
        }
    }

    /**
     * Marker class to indicate that no retriever should be used
     */
    final class NoRetriever implements Retriever<TextSegment> {

        @Override
        public List<TextSegment> findRelevant(String text) {
            throw new UnsupportedOperationException("should never be called");
        }
    }

    /**
     * Marker that is used to tell Quarkus to use the {@link AuditService} that the user has configured as a CDI bean.
     * If no such bean exists, then no audit service will be used.
     */
    final class BeanIfExistsAuditServiceSupplier implements Supplier<AuditService> {

        @Override
        public AuditService get() {
            throw new UnsupportedOperationException("should never be called");
        }
    }

    /**
     * Marker that is used to tell Quarkus to use the {@link ModerationModel} that has been configured as a CDI bean by
     * any of the extensions providing such capability (such as {@code quarkus-langchain4j-openai} and
     * {@code quarkus-langchain4j-azure-openai}).
     */
    final class BeanModerationModelSupplier implements Supplier<ModerationModel> {

        @Override
        public ModerationModel get() {
            throw new UnsupportedOperationException("should never be called");
        }
    }

    /**
     * Marker class to indicate that no moderation model should be used
     */
    final class NoModerationModelSupplier implements Supplier<ModerationModel> {

        @Override
        public ModerationModel get() {
            throw new UnsupportedOperationException("should never be called");
        }
    }
}
