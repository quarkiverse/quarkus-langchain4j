package io.quarkiverse.langchain4j.sample;

import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.mcp.runtime.McpToolBox;

@RegisterAiService
public interface PoemService {
    @UserMessage("""
            Write a short 1 paragraph poem in {language} about a Java programming language.
            Please start by greeting the currently logged in user by name and asking to enjoy reading the poem.""")
    @McpToolBox("user-name")
    String writePoem(String language);
}