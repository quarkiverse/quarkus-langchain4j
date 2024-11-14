package io.quarkiverse.langchain4j.guardrails;

import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.core.type.TypeReference;

import dev.langchain4j.data.message.AiMessage;

public abstract class AbstractJsonExtractorOutputGuardrail implements OutputGuardrail {

    @Inject
    Logger logger;

    @Inject
    JsonGuardrailsUtils jsonGuardrailsUtils;

    protected AbstractJsonExtractorOutputGuardrail() {
        if (getOutputClass() == null && getOutputType() == null) {
            throw new IllegalArgumentException("Either getOutputClass() or getOutputType() must be implemented");
        }
    }

    @Override
    public OutputGuardrailResult validate(AiMessage responseFromLLM) {
        String llmResponse = responseFromLLM.text();
        logger.debugf("LLM output: %s", llmResponse);

        Object result = deserialize(llmResponse);
        if (result != null) {
            return successWith(llmResponse, result);
        }

        String json = jsonGuardrailsUtils.trimNonJson(llmResponse);
        if (json != null) {
            result = deserialize(json);
            if (result != null) {
                return successWith(json, result);
            }
        }

        return reprompt("Invalid JSON",
                "Make sure you return a valid JSON object following "
                        + "the specified format");
    }

    protected Object deserialize(String llmResponse) {
        return getOutputClass() != null ? jsonGuardrailsUtils.deserialize(llmResponse, getOutputClass())
                : jsonGuardrailsUtils.deserialize(llmResponse, getOutputType());
    }

    protected Class<?> getOutputClass() {
        return null;
    }

    protected TypeReference<?> getOutputType() {
        return null;
    }
}
