package io.quarkiverse.langchain4j.sample.guardrails;

import dev.langchain4j.agent.tool.Tool;
import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrails;
import io.quarkiverse.langchain4j.guardrails.ToolOutputGuardrails;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Example tools demonstrating various guardrail configurations.
 */
@ApplicationScoped
public class EmailTools {

    /**
     * Send email with input validation (email format and authorization).
     * This tool demonstrates:
     * - Email format validation (input guardrail)
     * - User authorization check (input guardrail)
     * - Multiple guardrails in chain
     */
    @Tool("Send an email to a recipient")
    @ToolInputGuardrails({
            EmailFormatValidator.class,
            UserAuthorizationGuardrail.class
    })
    public String sendEmail(String to,
                            String subject,
                            String body) {
        return "Email sent successfully to " + to +
                " with subject '" + subject + "'. Message: " + body;
    }

    /**
     * Send bulk email with rate limiting.
     * This tool demonstrates:
     * - Rate limiting (input guardrail)
     * - Preventing abuse through guardrails
     */
    @Tool("Send bulk emails to multiple recipients")
    @ToolInputGuardrails({RateLimitGuardrail.class})
    public String sendBulkEmail(String recipients,
                                String subject,
                                String body) {
        String[] emails = recipients.split(",");
        return "Bulk email sent to " + emails.length + " recipients: " + recipients;
    }

    /**
     * Get customer information with output filtering.
     * This tool demonstrates:
     * - Sensitive data filtering (output guardrail)
     * - Output size limiting (output guardrail)
     * - Protecting customer privacy
     */
    @Tool("Get customer information by customer ID")
    @ToolOutputGuardrails({
            SensitiveDataFilter.class,
            OutputSizeLimiter.class
    })
    public String getCustomerInfo(String customerId) {
        // Simulated customer data
        return "Customer ID: " + customerId + "\n" +
                "Name: John Doe\n" +
                "Email: john.doe@example.com\n" +
                "Phone: +1-555-123-4567\n" +
                "SSN: 123-45-6789\n" +  // This will be filtered by SensitiveDataFilter
                "Credit Card: 1234-5678-9012-3456\n" +  // This will be filtered
                "Address: 123 Main St, Anytown, USA\n" +
                "Account Balance: $15,234.56\n" +
                "Last Login: 2025-11-25 10:30:00";
    }

    /**
     * Search customer database (demonstrates output size limiting).
     * This tool demonstrates:
     * - Output size limiting to prevent token overuse
     * - Automatic truncation of large results
     */
    @Tool("Search customer database by query")
    @ToolOutputGuardrails({OutputSizeLimiter.class})
    public String searchCustomers(String query) {
        // Simulated search results (in real app, this would query a database)
        StringBuilder results = new StringBuilder();
        results.append("Search results for '").append(query).append("':\n\n");

        for (int i = 1; i <= 50; i++) {
            results.append("Customer ").append(i).append(":\n");
            results.append("  Name: Customer ").append(i).append(" Name\n");
            results.append("  Email: customer").append(i).append("@example.com\n");
            results.append("  Phone: +1-555-").append(String.format("%03d", i)).append("-").append(String.format("%04d", i * 10)).append("\n");
            results.append("  Status: Active\n\n");
        }

        return results.toString();
    }

    /**
     * Simple tool without guardrails for comparison.
     */
    @Tool("Get current timestamp")
    public String getCurrentTime() {
        return "Current time: " + java.time.LocalDateTime.now();
    }
}
