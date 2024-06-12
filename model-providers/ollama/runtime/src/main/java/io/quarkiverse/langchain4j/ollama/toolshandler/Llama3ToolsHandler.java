package io.quarkiverse.langchain4j.ollama.toolshandler;

import dev.langchain4j.model.input.PromptTemplate;
import io.quarkiverse.langchain4j.ollama.AbstractToolsHandler;

public class Llama3ToolsHandler extends AbstractToolsHandler {

    static final PromptTemplate DEFAULT_SYSTEM_TEMPLATE = PromptTemplate.from("""
            You are a helpful AI assistant responding to user requests delimited by "---".
            You have access to the following tools:
            {tools}
            Select the most appropriate tool for each user request and respond with a JSON object containing:
                - "tool": <selected tool name>
                - "tool_input": <required parameters matching the tool's JSON schema>
            Follow these guidelines:
                - Only use the listed tools.
                - Avoid using twice the same tool.
                - Use user history to avoid selecting the same tool with identical parameters more than once.
                - Retrieve precise data using the tools without inventing data or parameters.
                - Break down complex requests into sequential tool calls.
                - Combine user history and tool descriptions to choose the best next tool.
                - If a tool with the same parameters has been used, respond with "__conversational_response" using the previous result.
                - When enough information is gathered, respond with "__conversational_response" using the provided data.
            """);

    @Override
    public PromptTemplate getDefaultSystemTemplate() {
        return DEFAULT_SYSTEM_TEMPLATE;
    }
}
