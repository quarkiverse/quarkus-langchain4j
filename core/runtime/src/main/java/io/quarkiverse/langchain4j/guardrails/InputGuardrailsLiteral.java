package io.quarkiverse.langchain4j.guardrails;

import java.util.List;

import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.service.guardrail.InputGuardrails;

public final class InputGuardrailsLiteral extends ClassProvidingAnnotationLiteral<InputGuardrails, InputGuardrail>
        implements InputGuardrails {
    /**
     * Needed because this class will be serialized & deserialized
     */
    public InputGuardrailsLiteral() {
        this(List.of());
    }

    public InputGuardrailsLiteral(List<String> guardrailClassNames) {
        super(guardrailClassNames);
    }
}
