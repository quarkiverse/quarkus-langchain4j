package io.quarkiverse.langchain4j.memorystore.hibernate.test;

import java.util.List;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;

public final class MessageFixtures {
    private MessageFixtures() {
    }

    public static List<ChatMessage> messages() {
        return List.of(
                SystemMessage.from("Keep tool-call order"),
                UserMessage.from("你好，résumé 😀"),
                AiMessage.builder().thinking("compare alternatives").text("Calling lookup")
                        .toolExecutionRequests(List.of(ToolExecutionRequest.builder()
                                .id("call-1").name("lookup").arguments("{\"key\":\"x\"}").build()))
                        .build(),
                ToolExecutionResultMessage.builder().id("call-1").toolName("lookup")
                        .contents(TextContent.from("tool failed"), ImageContent.from("https://example.com/image.png"))
                        .isError(true).build(),
                AiMessage.from("Final answer"));
    }
}
