package io.quarkiverse.langchain4j;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a {@code static} method on an AI service interface as the handler for
 * the model's thinking/reasoning output. The method is invoked after any
 * non-streaming response of that service whose {@code AiMessage.thinking()}
 * is non-blank.
 * <p>
 * The handler must be {@code static}, return {@code void}, and take a single
 * {@link io.quarkiverse.langchain4j.runtime.aiservice.ThinkingEmitted}
 * parameter. Only one such handler is allowed per AI service interface.
 *
 * <pre>
 * {
 *     &#64;code
 *     &#64;RegisterAiService
 *     interface MathAssistant {
 *         String solve(String problem);
 *
 *         @Thinking
 *         static void onThinking(ThinkingEmitted event) {
 *             Log.infof("[%s] %s", event.methodName(), event.text());
 *         }
 *     }
 * }
 * </pre>
 *
 * This annotation does <strong>not</strong> enable thinking on the model
 * itself. Each provider exposes its own configuration knob for that (for
 * example {@code quarkus.langchain4j.ollama.chat-model.model-options.think}
 * on Ollama, or the equivalent reasoning parameter on Anthropic, Gemini,
 * OpenAI, etc.).
 * <p>
 * For the streaming path, use the {@code onPartialThinking} handler exposed
 * by {@code TokenStream} or observe {@code ChatEvent.PartialThinkingEvent}
 * when returning {@code Multi<ChatEvent>}.
 */
@Target(ElementType.METHOD)
@Retention(RUNTIME)
@Documented
public @interface Thinking {
}
