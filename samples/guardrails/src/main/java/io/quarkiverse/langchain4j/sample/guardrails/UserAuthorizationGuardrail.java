package io.quarkiverse.langchain4j.sample.guardrails;

import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrail;
import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrailRequest;
import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrailResult;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;

/**
 * Input guardrail that checks user authorization before executing tools.
 * NOTE: This is a simplified example. In a production environment,
 * you would configure proper Quarkus Security with authentication.
 */
@RequestScoped
public class UserAuthorizationGuardrail implements ToolInputGuardrail {

    @Inject
    SecurityIdentity securityIdentity;

    @Override
    public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
        // In this example, we simulate authorization check
        // In a real application, you would check actual roles and permissions

        String toolName = request.toolName();

        // For demonstration purposes, assume anonymous users cannot send emails
        if (securityIdentity.isAnonymous()) {
            return ToolInputGuardrailResult.failure(
                    "User not authorized to execute '" + toolName +
                            "'. Please authenticate to use this feature.");
        }

        // Check for required role (in a real app, this would be a real role check)
        // For this demo, we'll assume all authenticated users have the right role
        if (!hasRequiredRole(toolName)) {
            return ToolInputGuardrailResult.failure(
                    "User '" + securityIdentity.getPrincipal().getName() +
                            "' does not have permission to execute '" + toolName + "'.");
        }

        // Authorization successful
        return ToolInputGuardrailResult.success();
    }

    private boolean hasRequiredRole(String toolName) {
        // For this demo, we'll simulate that all authenticated users have permission
        // In production, use: securityIdentity.hasRole("REQUIRED_ROLE")
        return true;
    }
}
