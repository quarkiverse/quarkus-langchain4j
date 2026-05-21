package io.quarkiverse.langchain4j.runtime.aiservice;

/**
 * Receives thinking/reasoning events produced by an AI service.
 * <p>
 * Implementations are generated at build time, one per AI service that declares
 * an {@link io.quarkiverse.langchain4j.OnThinking @OnThinking}-annotated static
 * method on its interface. The generated class simply forwards the event to
 * that static method, so there is no reflection on the hot path.
 */
public interface ThinkingHandler {

    void emit(ThinkingEmitted event);
}
