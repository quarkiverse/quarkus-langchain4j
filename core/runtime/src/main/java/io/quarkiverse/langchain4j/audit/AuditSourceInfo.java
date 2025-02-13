package io.quarkiverse.langchain4j.audit;

import java.util.Optional;
import java.util.UUID;

/**
 * Contains information about the source of an audit event
 */
public interface AuditSourceInfo {
    /**
     * The fully-qualified name of the interface where the llm interaction was initialized
     *
     * @see #methodName()
     */
    String interfaceName();

    /**
     * The method name on {@link #interfaceName()} where the llm interaction was initiated
     *
     * @see #interfaceName()
     */
    String methodName();

    /**
     * The position of the memory id parameter in {@link #methodParams()}, if one exists
     */
    Optional<Integer> memoryIDParamPosition();

    /**
     * The parameters passed into the initial LLM call
     */
    Object[] methodParams();

    /**
     * A unique identifier that identifies this entire interaction with the LLM
     */
    UUID interactionId();
}
