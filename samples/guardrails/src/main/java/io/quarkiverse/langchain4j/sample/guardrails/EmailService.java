package io.quarkiverse.langchain4j.sample.guardrails;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.ToolBox;

/**
 * AI Service for email-related operations.
 * This service demonstrates how tools with guardrails work in practice.
 */
@RegisterAiService
public interface EmailService {

    /**
     * Process email-related requests.
     * The LLM will use the available tools (with their guardrails) to fulfill the request.
     */
    @SystemMessage("""
            You are an AI assistant that helps with email operations.
            You have access to tools for sending emails and retrieving customer information.
            
            Important:
            - Always provide valid email addresses when sending emails
            - Follow rate limits and authorization requirements
            - Be aware that sensitive data may be filtered from customer information
            """)
    @UserMessage("{userMessage}")
    @ToolBox(EmailTools.class)
    String processRequest(String userMessage);
}
