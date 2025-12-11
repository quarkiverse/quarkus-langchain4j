package io.quarkiverse.langchain4j.sample.chatbot;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

/**
 * A simple customer support chatbot that answers questions about
 * a fictional e-commerce company.
 */
@RegisterAiService
@ApplicationScoped
public interface CustomerSupportBot {

    @SystemMessage("""
            You are a helpful customer support assistant for QuarkusShop, an online e-commerce store.

            Company Information:
            - Business hours: Monday-Friday, 9 AM - 5 PM EST
            - Shipping: Free shipping on orders over $50
            - Returns: 30-day return policy
            - Contact: support@quarkusshop.com or 1-800-QUARKUS

            Provide helpful, concise, and friendly responses to customer questions.
            """)
    String chat(@UserMessage String message);
}
