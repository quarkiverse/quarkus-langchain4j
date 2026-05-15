package io.quarkiverse.langchain4j.runtime.config;

import io.quarkus.runtime.annotations.ConfigGroup;

/**
 * Per-AiService {@code tools} configuration sub-tree.
 *
 * <pre>
 * quarkus.langchain4j.&lt;service-name&gt;.tools.execution=virtual-threads
 * </pre>
 */
@ConfigGroup
public interface AiServiceToolsConfig {

    /**
     * Per-AiService tool-execution overrides.
     */
    AiServiceToolsExecutionConfig execution();
}
