package io.quarkiverse.langchain4j.sample.guardrails;

import dev.langchain4j.service.tool.ToolExecutionResult;
import io.quarkiverse.langchain4j.guardrails.ToolOutputGuardrail;
import io.quarkiverse.langchain4j.guardrails.ToolOutputGuardrailRequest;
import io.quarkiverse.langchain4j.guardrails.ToolOutputGuardrailResult;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Output guardrail that limits the size of tool output.
 */
@ApplicationScoped
public class OutputSizeLimiter implements ToolOutputGuardrail {

    // Maximum characters allowed (roughly ~1000 tokens)
    private static final int MAX_CHARACTERS = 4000;

    @Override
    public ToolOutputGuardrailResult validate(ToolOutputGuardrailRequest request) {
        String result = request.resultText();

        if (result == null || result.isEmpty()) {
            return ToolOutputGuardrailResult.success();
        }

        // Check if output exceeds limit
        if (result.length() > MAX_CHARACTERS) {
            // Truncate the output
            String truncated = result.substring(0, MAX_CHARACTERS);

            // Add truncation notice
            truncated += "\n\n[Output truncated - " + (result.length() - MAX_CHARACTERS) +
                    " characters omitted. Original length: " + result.length() + " characters. " +
                    "Please refine your query to get more specific results.]";

            // Return modified result
            ToolExecutionResult modifiedResult = ToolExecutionResult.builder()
                    .resultText(truncated)
                    .build();

            return ToolOutputGuardrailResult.successWith(modifiedResult);
        }

        // Output size is acceptable
        return ToolOutputGuardrailResult.success();
    }
}
