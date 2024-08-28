package io.quarkiverse.langchain4j.sample.chatbot;

import dev.langchain4j.service.UserMessage;
import dev.langchain4j.web.search.WebSearchTool;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.SessionScoped;

@RegisterAiService(tools = {WebSearchTool.class, AdditionalTools.class})
@SessionScoped
public interface Bot {

    String chat(@UserMessage String question);

}
