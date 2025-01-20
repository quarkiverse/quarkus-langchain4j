package io.quarkiverse.langchain4j.guardrails;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.rag.AugmentationResult;

/**
 * Represents the parameter passed to {@link OutputGuardrail#validate(OutputGuardrailParams)}.
 *
 * @param responseFromLLM the response from the LLM
 * @param memory the memory, can be {@code null} or empty
 * @param augmentationResult the augmentation result, can be {@code null}
 * @param userMessageTemplate the user message template, cannot be {@code null}
 * @param variables the variable to be used with userMessageTemplate, cannot be {@code null}
 */
public record OutputGuardrailParams(AiMessage responseFromLLM, ChatMemory memory,
        AugmentationResult augmentationResult, String userMessageTemplate,
        Map<String, Object> variables) implements GuardrailParams {

    public static OutputGuardrailParams from(AiMessage responseFromLLM) {
        return from(responseFromLLM, null);
    }

    public static OutputGuardrailParams from(AiMessage responseFromLLM, Map<String, Object> variables) {
        return new OutputGuardrailParams(responseFromLLM, null, null, null, Optional.ofNullable(variables).orElseGet(Map::of));
    }

    @Override
    public OutputGuardrailParams withText(String text) {
        List<ToolExecutionRequest> tools = responseFromLLM.toolExecutionRequests();
        AiMessage aiMessage = tools != null && !tools.isEmpty() ? new AiMessage(text, tools) : new AiMessage(text);
        return new OutputGuardrailParams(aiMessage, memory, augmentationResult, userMessageTemplate, variables);
    }
}
