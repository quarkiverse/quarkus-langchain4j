package io.quarkiverse.langchain4j.runtime.tool.guardrails;

import java.util.List;

import io.quarkiverse.langchain4j.guardrails.ToolOutputGuardrail;
import io.quarkiverse.langchain4j.guardrails.ToolOutputGuardrails;

/**
 * Annotation literal for {@link ToolOutputGuardrails}.
 *
 * @see ToolOutputGuardrails
 * @see ToolGuardrailAnnotationLiteral
 */
public final class ToolOutputGuardrailsLiteral
        extends ToolGuardrailAnnotationLiteral<ToolOutputGuardrails, ToolOutputGuardrail>
        implements ToolOutputGuardrails {

    /**
     * Default constructor required for serialization/deserialization.
     * <p>
     * Creates an empty literal with no guardrails configured.
     * </p>
     */
    public ToolOutputGuardrailsLiteral() {
        this(List.of());
    }

    /**
     * Constructs a new tool output guardrails literal with the specified guardrail class names.
     *
     * @param guardrailClassNames the list of fully qualified guardrail class names
     */
    public ToolOutputGuardrailsLiteral(List<String> guardrailClassNames) {
        super(guardrailClassNames);
    }
}
