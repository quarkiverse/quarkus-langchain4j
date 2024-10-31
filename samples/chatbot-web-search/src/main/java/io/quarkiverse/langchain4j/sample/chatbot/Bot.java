package io.quarkiverse.langchain4j.sample.chatbot;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.web.search.WebSearchTool;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.SessionScoped;

@RegisterAiService(tools = {WebSearchTool.class, AdditionalTools.class})
@SessionScoped
public interface Bot {

    @SystemMessage("""
            You have access to a function that looks up data using the Tavily web search engine.
            The web search engine doesn't understand the concept of 'today',
            so if the user asks something
            that requires the knowledge of today's date, use the getTodaysDate function before
            calling the search engine.
            """)
    String chat(@UserMessage String question);

}
