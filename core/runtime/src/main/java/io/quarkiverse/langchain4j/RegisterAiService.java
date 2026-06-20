package io.quarkiverse.langchain4j;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import io.quarkiverse.langchain4j.runtime.aiservice.ChatMemoryFlushStrategy;
import io.quarkiverse.langchain4j.runtime.aiservice.SystemMessageProvider;
import io.quarkiverse.langchain4j.runtime.aiservice.SystemMessageProviderWithContext;

/**
 * Used to create LangChain4j's {@link AiServices} in a declarative manner that the application can then use simply by
 * using the class as a CDI bean.
 * Under the hood LangChain4j's {@link AiServices#builder(Class)} is called
 * while also providing the builder with the proper {@link ChatModel} bean (mandatory), {@code tools} bean (optional),
 * {@link ChatMemoryProvider} and {@link ContentRetriever} beans (which by default are configured if such beans exist).
 * <p>
 * Component attributes use a tri-state resolution model:
 * <ul>
 * <li>{@code void.class} — disabled / not configured (SKIP)</li>
 * <li>Interface type (e.g. {@code ChatMemoryProvider.class}) — auto-discover via CDI (AUTO_DISCOVER)</li>
 * <li>Concrete class — inject a specific CDI bean by type (EXPLICIT)</li>
 * </ul>
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
     * Selects the {@link ChatModel} CDI bean to use by name.
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
     * Defines the maximum number of LLM request/response round trips while handling a single chat request.
     * If this number is exceeded, the chat request will fail.
     * If not specified (left to zero) for a specific AI service,
     * the AI service will use the value of the common {@code quarkus.langchain4j.ai-service.max-tool-calling-round-trips}
     * property.
     * If that property is unset too, the default is 10 round trips.
     */
    int maxToolCallingRoundTrips() default 0;

    /**
     * Defines the maximum number of tool calls allowed per single LLM response.
     * If this number is exceeded, a {@code ToolCallsLimitExceededException} will be thrown.
     * If not specified (left to zero) for a specific AI service,
     * the AI service will use the value of the common {@code quarkus.langchain4j.ai-service.max-tool-calls-per-response}
     * property.
     * If that property is unset too, the default is {@code 0} (unlimited).
     */
    int maxToolCallsPerResponse() default 0;

    /**
     * Tool classes to use. All tools are expected to be CDI beans.
     */
    Class<?>[] tools() default {};

    /**
     * Strategy to be used when the AI service hallucinates the tool name.
     * <p>
     * Use {@code void.class} to disable. Set to a concrete implementation class
     * to inject a specific CDI bean.
     */
    Class<?> toolHallucinationStrategy() default void.class;

    /**
     * Configures the {@link ChatMemoryProvider} to use.
     * <p>
     * By default ({@code ChatMemoryProvider.class}), Quarkus auto-discovers a CDI bean implementing
     * {@link ChatMemoryProvider}. Quarkus itself provides a default bean that uses an {@link InMemoryChatMemoryStore}
     * as the backing store. The default type for the actual {@link ChatMemory} is {@link MessageWindowChatMemory}
     * and it is configured with the value of the {@code quarkus.langchain4j.chat-memory.memory-window.max-messages}
     * configuration property (which defaults to 10) as a way of limiting the number of messages in each chat.
     * <p>
     * If the application provides its own {@link ChatMemoryProvider} bean, that takes precedence over what Quarkus provides as
     * the default.
     * <p>
     * If the application provides an implementation of {@link ChatMemoryStore}, then that is used instead of the default
     * {@link InMemoryChatMemoryStore}.
     * <p>
     * Set to {@code void.class} to disable chat memory for this AI service.
     * Set to a concrete implementation class to inject a specific CDI bean.
     */
    Class<?> chatMemoryProvider() default ChatMemoryProvider.class;

    /**
     * Configures the flush strategy for the committable chat memory.
     * <p>
     * By default ({@code void.class}), Quarkus Langchain4j defers committing messages to the {@link ChatMemory}
     * until the AI service method completes successfully. This enables seamless
     * {@code @Retry} support — if a failure occurs, the uncommitted messages are discarded.
     * <p>
     * Set to a concrete {@link ChatMemoryFlushStrategy} implementation class to control this behavior.
     *
     * @see ChatMemoryFlushStrategy
     */
    Class<?> chatMemoryFlushStrategy() default void.class;

    /**
     * Configures the {@link RetrievalAugmentor} to use (when using RAG).
     * <p>
     * By default ({@code void.class}), no retrieval augmentor is used unless one is discovered via CDI.
     * Set to a concrete implementation class to inject a specific CDI bean.
     */
    Class<?> retrievalAugmentor() default void.class;

    /**
     * Configures the moderation model to use.
     * <p>
     * By default ({@code void.class}), Quarkus will auto-detect if one is needed (when methods are annotated
     * with {@code @Moderate}) and look for a CDI bean.
     * Set to a concrete implementation class to inject a specific CDI bean.
     */
    Class<?> moderationModel() default void.class;

    /**
     * Configures the tool provider to use.
     * <p>
     * By default ({@code void.class}), no tool provider is used unless one is discovered via CDI.
     * Set to a concrete implementation class to inject a specific CDI bean.
     */
    Class<?> toolProvider() default ToolProvider.class;

    /**
     * Configures the tool search strategy used to let the model discover tools dynamically at inference time
     * instead of exposing the whole tool catalog upfront.
     * <p>
     * By default ({@code void.class}), no tool search strategy is used unless one is discovered via CDI.
     * Set to a concrete implementation class to inject a specific CDI bean.
     */
    Class<?> toolSearchStrategy() default void.class;

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
     * Configures a system message provider to dynamically supply a system message at runtime.
     * Provide either a {@link SystemMessageProvider} (based on the memory ID) or a
     * {@link SystemMessageProviderWithContext} (based on the invocation context, so the system message can vary by model).
     * <p>
     * By default ({@code void.class}), no dynamic system message provider is used. In that case,
     * the system message can still be provided via the {@link dev.langchain4j.service.SystemMessage} annotation
     * on the AiService method.
     *
     * @see <a href="https://docs.langchain4j.dev/tutorials/ai-services#system-message-provider">LangChain4j System Message
     *      Provider</a>
     */
    Class<?> systemMessageProvider() default void.class;

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

}
