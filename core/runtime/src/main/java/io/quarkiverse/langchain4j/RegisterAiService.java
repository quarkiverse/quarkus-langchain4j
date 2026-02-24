package io.quarkiverse.langchain4j;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import io.quarkiverse.langchain4j.runtime.aiservice.SystemMessageProvider;

/**
 * Used to create LangChain4j's {@link AiServices} in a declarative manner that the application can then use simply by
 * using the class as a CDI bean.
 * Under the hood LangChain4j's {@link AiServices#builder(Class)} is called
 * while also providing the builder with the proper {@link ChatModel} bean (mandatory), {@code tools} bean (optional),
 * {@link ChatMemoryProvider} and {@link ContentRetriever} beans (which by default are configured if such beans exist).
 * <p>
 * NOTE: The resulting CDI bean is {@link jakarta.enterprise.context.RequestScoped} by default. If you need to change the scope,
 * simply annotate the class with a CDI scope.
 * CAUTION: When using anything other than the request scope, you need to be very careful with the chat memory implementation.
 * <p>
 * NOTE: When the application also contains the {@code quarkus-micrometer} extension, metrics are automatically generated
 * for the method invocations.
 * <p>
 * See also {@link ToolBox}, {@link SeedMemory}
 */
@Retention(RUNTIME)
@Target(ElementType.TYPE)
public @interface RegisterAiService {

    /**
     * Configures the way to obtain the {@link StreamingChatModel} to use.
     * If not configured, the default CDI bean implementing the model is looked up.
     * Such a bean provided automatically by extensions such as {@code quarkus-langchain4j-openai},
     * {@code quarkus-langchain4j-azure-openai} or
     * {@code quarkus-langchain4j-hugging-face}
     */
    Class<? extends Supplier<StreamingChatModel>> streamingChatLanguageModelSupplier() default BeanStreamingChatLanguageModelSupplier.class;

    /**
     * Configures the way to obtain the {@link ChatModel} to use.
     * If not configured, the default CDI bean implementing the model is looked up.
     * Such a bean provided automatically by extensions such as {@code quarkus-langchain4j-openai},
     * {@code quarkus-langchain4j-azure-openai} or
     * {@code quarkus-langchain4j-hugging-face}
     */
    Class<? extends Supplier<ChatModel>> chatLanguageModelSupplier() default BeanChatLanguageModelSupplier.class;

    /**
     * When {@code chatLanguageModelSupplier} is set to {@code BeanChatLanguageModelSupplier.class} (which is the default)
     * this allows the selection of the {@link ChatModel} CDI bean to use.
     * <p>
     * If not set, the default model (i.e. the one configured without setting the model name) is used.
     * An example of the default model configuration is the following:
     * {@code quarkus.langchain4j.openai.chat-model.model-name=gpt-4-turbo-preview}
     * <p>
     * If set, it uses the model configured by name. For example if this is set to {@code somename}
     * an example configuration value for that named model could be:
     * {@code quarkus.langchain4j.somename.openai.chat-model.model-name=gpt-4-turbo-preview}
     * <p>
     * If the model needs to be selected at runtime, then {@link ModelName} can be used as a method parameter of an AiService.
     */
    String modelName() default "<default>";

    /**
     * Defines the maximum number of sequential calls to tools while handling a single chat request.
     * If this number is exceeded, the chat request will fail.
     * If not specified (left to zero) for a specific AI service,
     * the AI service will use the value of the common {@code quarkus.langchain4j.ai-service.max-tool-executions} property.
     * If that property is unset too, the default is 10 invocations.
     */
    int maxSequentialToolInvocations() default 0;

    /**
     * Tool classes to use. All tools are expected to be CDI beans.
     */
    Class<?>[] tools() default {};

    /**
     * Strategy to be used when the AI service hallucinates the tool name.
     */
    Class<? extends Function<ToolExecutionRequest, ToolExecutionResultMessage>> toolHallucinationStrategy() default BeanIfExistsToolHallucinationStrategy.class;

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
     * Configures the way to obtain the {@link RetrievalAugmentor} to use
     * (when using RAG). The Supplier may or may not be a CDI bean (but most
     * typically it will, so consider adding a bean-defining annotation to
     * it). If it is not a CDI bean, Quarkus will create an instance
     * by calling its no-arg constructor.
     * <p>
     * If unspecified, Quarkus will attempt to locate a CDI bean that
     * implements {@link RetrievalAugmentor} and use it if one exists.
     */
    Class<? extends Supplier<RetrievalAugmentor>> retrievalAugmentor() default BeanIfExistsRetrievalAugmentorSupplier.class;

    /**
     * Configures the way to obtain the {@link ModerationModel} to use.
     * By default, Quarkus will look for a CDI bean that implements {@link ModerationModel} if at least one method is annotated
     * with @Moderate.
     * If an arbitrary {@link ModerationModel} instance is needed, a custom implementation of {@link Supplier<ModerationModel>}
     * needs to be provided.
     */
    Class<? extends Supplier<ModerationModel>> moderationModelSupplier() default BeanIfExistsModerationModelSupplier.class;

    /**
     * Configures a toolProviderSupplier. It is possible to use together toolProviderSupplier and "normal" tools.
     */
    Class<? extends Supplier<ToolProvider>> toolProviderSupplier() default BeanIfExistsToolProviderSupplier.class;

    /**
     * By default, after first tool call execution, in subsequent prompts the {@code toolChoice} of
     * {@link dev.langchain4j.model.chat.request.ChatRequestParameters}
     * is set to {@link ToolChoice#AUTO}.
     * By enabling this option {@link ToolChoice#AUTO} will not be set and instead whatever value was used in the initial prompt
     * will
     * continue to be used.
     * <p>
     * BEWARE: This is dangerous as it can result in an infinite-loop when using the AiService in combination with the
     * {@code toolChoice} option set to {@link ToolChoice#REQUIRED}.
     */
    boolean allowContinuousForcedToolCalling() default false;

    /**
     * Configures a {@link SystemMessageProvider} to dynamically supply a system message based on the memory ID.
     * This is useful when the system message needs to be determined at runtime, for example based on user context
     * or other dynamic factors.
     * <p>
     * If not configured (left at the default), no dynamic system message provider is used. In that case,
     * the system message can still be provided via the {@link dev.langchain4j.service.SystemMessage} annotation
     * on the AiService method.
     *
     * @see <a href="https://docs.langchain4j.dev/tutorials/ai-services#system-message-provider">LangChain4j System Message
     *      Provider</a>
     */
    Class<? extends SystemMessageProvider> systemMessageProviderSupplier() default NoSystemMessageProviderSupplier.class;

    /**
     * Indicates whether exceptions thrown during
     * <a href="https://docs.langchain4j.dev/tutorials/observability#types-of-events">AI service event execution</a>
     * should be rethrown during an AI service interaction, or should be quietly &quot;eaten&quot; by the event handler.
     * <p>
     * If {@code true}, exceptions thrown from AI Service event execution will be rethrown by an AI service interaction. This
     * also means that exceptions can be caught
     * and dealt with by application code should an AI service event handler fail for some reason.
     * <p>
     * Otherwise, errors will be handled silently.
     * <p>
     * Default is {@code false} to preserve backwards compatibility.
     */
    boolean shouldThrowExceptionOnEventError() default false;

    /**
     * Marker that is used to tell Quarkus to use the {@link ChatModel} that has been configured as a CDI bean by
     * any of the extensions providing such capability (such as {@code quarkus-langchain4j-openai} and
     * {@code quarkus-langchain4j-hugging-face}).
     */
    final class BeanChatLanguageModelSupplier implements Supplier<ChatModel> {

        @Override
        public ChatModel get() {
            throw new UnsupportedOperationException("should never be called");
        }
    }

    /**
     * Marker that is used to tell Quarkus to use the {@link StreamingChatModel} that has been configured as a CDI bean
     * by * any of the extensions providing such capability (such as {@code quarkus-langchain4j-openai} and
     * {@code quarkus-langchain4j-hugging-face}).
     */
    final class BeanStreamingChatLanguageModelSupplier implements Supplier<StreamingChatModel> {

        @Override
        public StreamingChatModel get() {
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
     * Marker that is used when the user does not want any memory configured for the AiService
     */
    final class NoChatMemoryProviderSupplier implements Supplier<ChatMemoryProvider> {

        @Override
        public ChatMemoryProvider get() {
            throw new UnsupportedOperationException("should never be called");
        }
    }

    /**
     * Marker class to indicate that no retriever should be used
     */
    final class NoRetriever implements ContentRetriever {

        @Override
        public List<Content> retrieve(Query query) {
            throw new UnsupportedOperationException("should never be called");
        }
    }

    /**
     * Marker that is used when the user does not want any tool provider
     */
    final class NoToolProviderSupplier implements Supplier<ToolProvider> {

        @Override
        public ToolProvider get() {
            throw new UnsupportedOperationException("should never be called");
        }
    }

    /**
     * Marker that is used to tell Quarkus to use the {@link RetrievalAugmentor} that the user has configured as a CDI bean.
     * If no such bean exists, then no retrieval augmentor will be used.
     */
    final class BeanIfExistsRetrievalAugmentorSupplier implements Supplier<RetrievalAugmentor> {

        @Override
        public RetrievalAugmentor get() {
            throw new UnsupportedOperationException("should never be called");
        }
    }

    /**
     * Marker that is used to tell Quarkus to not use any retrieval augmentor even if a CDI bean implementing
     * the `RetrievalAugmentor` interface exists.
     */
    final class NoRetrievalAugmentorSupplier implements Supplier<RetrievalAugmentor> {

        @Override
        public RetrievalAugmentor get() {
            throw new UnsupportedOperationException("should never be called");
        }
    }

    /**
     * Marker that is used to tell Quarkus to use the {@link ModerationModel} that the user has configured as a CDI bean.
     * If no such bean exists, then no audit service will be used.
     */
    final class BeanIfExistsModerationModelSupplier implements Supplier<ModerationModel> {

        @Override
        public ModerationModel get() {
            throw new UnsupportedOperationException("should never be called");
        }
    }

    /**
     * Marker that is used to tell Quarkus to use the {@link ImageModel} that the user has configured as a CDI bean.
     * If no such bean exists, then no audit service will be used.
     */
    final class BeanIfExistsImageModelSupplier implements Supplier<ImageModel> {

        @Override
        public ImageModel get() {
            throw new UnsupportedOperationException("should never be called");
        }
    }

    /**
     * Marker that is used to tell Quarkus to use the {@link ToolProvider} that the user has configured as a CDI bean.
     * If no such bean exists, then no tool provider will be used.
     */
    final class BeanIfExistsToolProviderSupplier implements Supplier<ToolProvider> {

        @Override
        public ToolProvider get() {
            throw new UnsupportedOperationException("should never be called");
        }
    }

    /**
     * Marker that is used to tell Quarkus to use the ToolHallucinationStrategy that the user has configured as a CDI bean.
     * If no such bean exists, then the default LangChain4j strategy will be used.
     */
    final class BeanIfExistsToolHallucinationStrategy implements Function<ToolExecutionRequest, ToolExecutionResultMessage> {

        @Override
        public ToolExecutionResultMessage apply(ToolExecutionRequest toolExecutionRequest) {
            throw new UnsupportedOperationException("should never be called");
        }
    }

    /**
     * Marker that is used when the user does not want any {@link SystemMessageProvider} configured for the AiService.
     * This is the default.
     */
    final class NoSystemMessageProviderSupplier implements SystemMessageProvider {

        @Override
        public java.util.Optional<String> getSystemMessage(Object memoryId) {
            throw new UnsupportedOperationException("should never be called");
        }
    }

    /**
     * Marker that is used to tell Quarkus to use the {@link SystemMessageProvider} that the user has configured as a CDI bean.
     * If no such bean exists, then no system message provider will be used.
     */
    final class BeanIfExistsSystemMessageProviderSupplier implements SystemMessageProvider {

        @Override
        public java.util.Optional<String> getSystemMessage(Object memoryId) {
            throw new UnsupportedOperationException("should never be called");
        }
    }
}
