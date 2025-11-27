package io.quarkiverse.langchain4j.sample.guardrails;

import dev.langchain4j.service.tool.ToolExecutionResult;
import io.quarkiverse.langchain4j.guardrails.ToolOutputGuardrail;
import io.quarkiverse.langchain4j.guardrails.ToolOutputGuardrailRequest;
import io.quarkiverse.langchain4j.guardrails.ToolOutputGuardrailResult;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.regex.Pattern;

/**
 * Output guardrail that filters sensitive data from tool results.
 */
@ApplicationScoped
public class SensitiveDataFilter implements ToolOutputGuardrail {

    // Patterns for sensitive data
    private static final Pattern SSN_PATTERN = Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b");
    private static final Pattern CREDIT_CARD_PATTERN =
            Pattern.compile("\\b\\d{4}[- ]?\\d{4}[- ]?\\d{4}[- ]?\\d{4}\\b");
    // Optionally filter phone numbers (commented out as they might be needed)
    // private static final Pattern PHONE_PATTERN = Pattern.compile("\\+?1?[- ]?\\(?\\d{3}\\)?[- ]?\\d{3}[- ]?\\d{4}");

    @Override
    public ToolOutputGuardrailResult validate(ToolOutputGuardrailRequest request) {
        String result = request.resultText();

        if (result == null || result.isEmpty()) {
            return ToolOutputGuardrailResult.success();
        }

        // Apply filters
        String filtered = result;
        filtered = SSN_PATTERN.matcher(filtered).replaceAll("[REDACTED-SSN]");
        filtered = CREDIT_CARD_PATTERN.matcher(filtered).replaceAll("[REDACTED-CARD]");

        // If content was modified, return the filtered version
        if (!filtered.equals(result)) {
            ToolExecutionResult modifiedResult = ToolExecutionResult.builder()
                    .resultText(filtered)
                    .build();

            return ToolOutputGuardrailResult.successWith(modifiedResult);
        }

        // No sensitive data found
        return ToolOutputGuardrailResult.success();
    }
}
