package org.acme.example.openai.chat.ollama;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(tools = Tools.ExpenseService.class)
public interface PropertyManagerAssistant {
    @SystemMessage("""
            You are a property manager assistant, answering to co-owners requests.
            Format the date as YYYY-MM-DD and the time as HH:MM
            Today is {{current_date_time}} use this date as date time reference
            The co-owners is living in the following condominium: {condominium}
            """)
    @UserMessage("""
            {{request}}
            """)
    String answer(String condominium, String request);
}
