package io.quarkiverse.langchain4j.sample.guardrails;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrail;
import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrailRequest;
import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrailResult;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.regex.Pattern;

/**
 * Input guardrail that validates email addresses.
 * This guardrail checks if the email parameter matches a valid email format.
 * If validation fails, the tool execution is prevented and an error message is returned to the LLM.
 */
@ApplicationScoped
public class EmailFormatValidator implements ToolInputGuardrail {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
        try {
            // Parse the JSON arguments
            JsonNode args = objectMapper.readTree(request.arguments());

            // Check for 'to' parameter (single email)
            if (args.has("to")) {
                String email = args.get("to").asText();
                if (!isValidEmail(email)) {
                    return ToolInputGuardrailResult.failure(
                            "Invalid email format: '" + email +
                                    "'. Please provide a valid email address in the format 'user@domain.com'.");
                }
            }

            // Check for 'recipients' parameter (comma-separated emails)
            if (args.has("recipients")) {
                String recipients = args.get("recipients").asText();
                String[] emails = recipients.split(",");
                for (String email : emails) {
                    String trimmed = email.trim();
                    if (!isValidEmail(trimmed)) {
                        return ToolInputGuardrailResult.failure(
                                "Invalid email format in recipients list: '" + trimmed +
                                        "'. Please provide valid email addresses separated by commas.");
                    }
                }
            }

            // Validation passed
            return ToolInputGuardrailResult.success();

        } catch (Exception e) {
            return ToolInputGuardrailResult.fatal(
                    "Failed to validate email format: " + e.getMessage(), e);
        }
    }

    private boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email).matches();
    }
}
