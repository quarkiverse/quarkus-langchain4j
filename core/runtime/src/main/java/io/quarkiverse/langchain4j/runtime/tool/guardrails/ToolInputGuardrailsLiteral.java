package io.quarkiverse.langchain4j.runtime.tool.guardrails;

import java.util.List;

import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrail;
import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrails;

/**
 * Annotation literal for {@link ToolInputGuardrails}.
 *
 * @see ToolInputGuardrails
 * @see ToolGuardrailAnnotationLiteral
 */
public final class ToolInputGuardrailsLiteral
        extends ToolGuardrailAnnotationLiteral<ToolInputGuardrails, ToolInputGuardrail>
        implements ToolInputGuardrails {

    /**
     * Default constructor required for serialization/deserialization.
     * <p>
     * Creates an empty literal with no guardrails configured.
     * </p>
     */
    public ToolInputGuardrailsLiteral() {
        this(List.of());
    }

    /**
     * Constructs a new tool input guardrails literal with the specified guardrail class names.
     *
     * @param guardrailClassNames the list of fully qualified guardrail class names
     */
    public ToolInputGuardrailsLiteral(List<String> guardrailClassNames) {
        super(guardrailClassNames);
    }
}
