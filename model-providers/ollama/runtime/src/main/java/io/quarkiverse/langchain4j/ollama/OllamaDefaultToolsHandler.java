package io.quarkiverse.langchain4j.ollama;

import static io.quarkiverse.langchain4j.ollama.ChatRequest.Builder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolParameters;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.internal.Json;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;

public class OllamaDefaultToolsHandler implements ToolsHandler {

    static final PromptTemplate DEFAULT_SYSTEM_TEMPLATE = PromptTemplate.from("""
            You have access to the following tools:

            {tools}

            You must always select one of the above tools and respond with a JSON object matching the following schema,
            and only this json object:
            {
              "tool": <name of the selected tool>,
              "tool_input": <parameters for the selected tool, matching the tool's JSON schema>
            }
            Do not use other tools than the ones from the list above. Always provide the "tool_input" field.
            If several tools are necessary, answer them sequentially.

            When the user provides sufficient information, answer with the __conversational_response tool.
            """);

    static final ToolSpecification DEFAULT_RESPONSE_TOOL = ToolSpecification.builder()
            .name("__conversational_response")
            .description("Respond conversationally if no other tools should be called for a given query and history.")
            .parameters(ToolParameters.builder()
                    .type("object")
                    .properties(
                            Map.of("reponse",
                                    Map.of("type", "string",
                                            "description", "Conversational response to the user.")))
                    .required(Collections.singletonList("response"))
                    .build())
            .build();

    @Override
    public Builder enhanceWithTools(Builder builder, List<Message> messages, List<ToolSpecification> toolSpecifications,
            ToolSpecification toolThatMustBeExecuted) {
        if (toolSpecifications == null || toolSpecifications.isEmpty()) {
            return builder;
        }
        // Set Json Format
        builder.format("json");

        // Construct prompt with tools toolSpecifications and DEFAULT_RESPONSE_TOOL
        List<ToolSpecification> extendedList = new ArrayList<>(toolSpecifications.size() + 1);
        extendedList.addAll(toolSpecifications);
        extendedList.add(DEFAULT_RESPONSE_TOOL);
        Prompt prompt = DEFAULT_SYSTEM_TEMPLATE.apply(
                Map.of("tools", Json.toJson(extendedList)));

        // TODO handle -> toolThatMustBeExecuted skipped for the moment
        String initialSystemMessages = messages.stream().filter(cm -> cm.role() == Role.SYSTEM)
                .map(Message::content)
                .collect(Collectors.joining("\n"));

        Message groupedSystemMessage = Message.builder()
                .role(Role.SYSTEM)
                .content(initialSystemMessages + "\n" + prompt.text())
                .build();

        List<Message> otherMessages = messages.stream().filter(cm -> cm.role() != Role.SYSTEM).toList();

        // Add specific tools message
        List<Message> messagesWithTools = new ArrayList<>(messages.size() + 1);
        messagesWithTools.add(groupedSystemMessage);
        messagesWithTools.addAll(otherMessages);

        builder.messages(messagesWithTools);

        return builder;
    }

    @Override
    public AiMessage getAiMessageFromResponse(ChatResponse response, List<ToolSpecification> toolSpecifications) {
        ToolResponse toolResponse;
        try {
            // Extract tools
            toolResponse = Json.fromJson(response.message().content(), ToolResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("Ollama server did not respond with valid JSON. Please try again!");
        }
        // If the tool is the final result with default response tool
        if (toolResponse.tool.equals(DEFAULT_RESPONSE_TOOL.name())) {
            return AiMessage.from(toolResponse.tool_input.get("response").toString());
        }
        // Check if tool is part of the available tools
        List<String> availableTools = toolSpecifications.stream().map(ToolSpecification::name).toList();
        if (!availableTools.contains(toolResponse.tool)) {
            return AiMessage.from(String.format(
                    "Ollama server wants to call a tool '%s' that is not part of the available tools %s",
                    toolResponse.tool, availableTools));
        }
        // Extract tools request from response
        List<ToolExecutionRequest> toolExecutionRequests = toToolExecutionRequests(toolResponse, toolSpecifications);
        return AiMessage.aiMessage(toolExecutionRequests);
    }

    record ToolResponse(String tool, Map<String, Object> tool_input) {
    }

    private List<ToolExecutionRequest> toToolExecutionRequests(ToolResponse toolResponse,
            List<ToolSpecification> toolSpecifications) {
        return toolSpecifications.stream()
                .filter(ts -> ts.name().equals(toolResponse.tool))
                .map(ts -> toToolExecutionRequest(toolResponse, ts))
                .toList();
    }

    static ToolExecutionRequest toToolExecutionRequest(ToolResponse toolResponse, ToolSpecification toolSpecification) {
        return ToolExecutionRequest.builder()
                .name(toolSpecification.name())
                .arguments(Json.toJson(toolResponse.tool_input))
                .build();
    }
}
