package io.quarkiverse.langchain4j.agentic.runtime;

/**
 * Supplier types eligible for CDI fallback auto-wiring when no static
 * supplier is declared on the agent interface.
 * <p>
 * All types in this enum have overwrite-semantic setters on {@code AgentBuilder}
 * (simple field assignment). The build-time check ensures CDI beans are only
 * wired when no static supplier exists, preventing silent overwrites.
 * <p>
 * {@code ToolProvider} is excluded — its append semantics and MCP type collision
 * make fallback auto-wiring inappropriate. {@code AgentListener} is excluded — it
 * uses a separate additive path ({@code Instance<AgentListener>} on every agent).
 */
public enum CdiSupplierType {
    CONTENT_RETRIEVER,
    CHAT_MEMORY,
    CHAT_MEMORY_PROVIDER,
    RETRIEVAL_AUGMENTOR
}
