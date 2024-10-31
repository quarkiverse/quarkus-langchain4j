package org.acme.tools;

import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.ToolBox;

@RegisterAiService
public interface AiService {
    @ToolBox(Calculator.class)
    public String chat(@UserMessage String message);
}
