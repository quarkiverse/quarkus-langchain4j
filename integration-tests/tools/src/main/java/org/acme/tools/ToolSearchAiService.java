package org.acme.tools;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(tools = BookingTool.class, toolSearchStrategySupplier = FixedToolSearchStrategySupplier.class)
public interface ToolSearchAiService {

    String chat(@UserMessage String message, @MemoryId Object id);
}
