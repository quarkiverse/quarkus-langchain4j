package io.quarkiverse.langchain4j.sample.guardrails;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

/**
 * REST endpoint demonstrating tool guardrails.
 */
@Path("/email")
public class EmailResource {

    private final EmailService emailService;

    public EmailResource(EmailService emailService) {
        this.emailService = emailService;
    }

    /**
     * Process an email-related request.
     * Examples:
     * - "Send an email to john@example.com with subject 'Hello' and body 'How are you?'"
     * - "Get customer information for customer ID 12345"
     * - "Search for customers named Smith"
     */
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String processRequest(@QueryParam("request") String request) {
        return emailService.processRequest(request);
    }
}
