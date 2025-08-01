package io.quarkiverse.langchain4j.sample;

import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.mcp.runtime.McpToolBox;

@RegisterAiService
public interface PoemService {
    @UserMessage("""
            Write a short 1 paragraph poem in {language} about a Java programming language.
            Provide a translation to English if the original poem language is not English.
            Dedicate the poem to the service account, refer to this account by its name.""")
    @McpToolBox("service-account-name")
    String writePoem(String language);
}
