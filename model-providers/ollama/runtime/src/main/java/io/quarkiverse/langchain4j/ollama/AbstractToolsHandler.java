package io.quarkiverse.langchain4j.ollama;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolParameters;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.internal.Json;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static io.quarkiverse.langchain4j.ollama.ChatRequest.Builder;

public abstract class AbstractToolsHandler implements ToolsHandler {

    static final Pattern PATTERN1 = Pattern.compile("Tool (.?) called with parameters");
    static final Pattern PATTERN2 = Pattern.compile("called with parameters (.?), do not call it anymore");

    abstract public PromptTemplate getDefaultSystemTemplate();

    static final ToolSpecification DEFAULT_RESPONSE_TOOL = ToolSpecification.builder()
            .name("__conversational_response")
            .description("Respond conversationally if no other tools should be called for a given query and history " +
                    "or if the user request have been done.")
            .parameters(ToolParameters.builder()
                    .type("object")
                    .properties(
                            Map.of("response",
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
        Prompt prompt = getDefaultSystemTemplate().apply(
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
        Message initialUserMessage = Message.builder()
                .role(Role.USER)
                .content("--- " + otherMessages.get(0).content() + " ---").build();

        List<Message> lastMessages = convertAssistantMessages(otherMessages.subList(1, otherMessages.size()));
        //        String lastMessagesGrouped = lastMessages.stream()
        //                .map(Message::content)
        //                .collect(Collectors.joining("\n"));
        //        Message lastMessage = Message.builder()
        //                .role(Role.ASSISTANT)
        //                .content(lastMessagesGrouped).build();
        // Add specific tools message
        List<Message> messagesWithTools = new ArrayList<>(messages.size() + 1);
        messagesWithTools.add(groupedSystemMessage);
        messagesWithTools.addAll(lastMessages);
        messagesWithTools.add(initialUserMessage);

        builder.messages(messagesWithTools);

        return builder;
    }

    private List<Message> convertAssistantMessages(List<Message> lastMessages) {
        List<Message> messages = new ArrayList<>();
        Message assistantMsg = null;
        for (Message msg : lastMessages) {
            if (msg.role() == Role.ASSISTANT) {
                assistantMsg = msg;
            } else if (msg.role() == Role.USER) {
                if (assistantMsg == null) {
                    messages.add(msg);
                    // throw new RuntimeException(" USER Message detected without corresponding ASSISTANT Message.");
                } else {
                    messages.add(Message.builder()
                            .role(Role.USER) // Should be ASSISTANT but does not work, to check if not related to num_ctx
                            .content(String.format("%s and the result is %s", assistantMsg.content(), msg.content())).build());
                    assistantMsg = null;
                }
            } else {
                throw new RuntimeException(
                        String.format("Message with role %s not allowed at this stage.", msg.role()));
            }
        }
        return messages;
    }

    @Override
    public AiMessage handleResponse(ChatResponse response, List<ToolSpecification> toolSpecifications) {
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
            throw new RuntimeException(String.format(
                    "Ollama server wants to call a tool '%s' that is not part of the available tools %s",
                    toolResponse.tool, availableTools));
        }
        // Extract tools request from response
        List<ToolExecutionRequest> toolExecutionRequests = toToolExecutionRequests(toolResponse, toolSpecifications);
        // only one tool will be used.
        // toolSpecifications.clear();
        // toolSpecifications.add(DEFAULT_RESPONSE_TOOL);
        return new AiMessage(toolResponse.toAiMessageText(), toolExecutionRequests);
    }

    record ToolResponse(String tool, Map<String, Object> tool_input) {

        public String toAiMessageText() {
            return String.format("Tool \"%s\" with parameters %s has been called", tool,
                    Json.toJson(tool_input).replace("\n", ""));
        }

        public ToolResponse fromAiMessageContent(String content) {
            Matcher matcher1 = PATTERN1.matcher(content);
            if (matcher1.find()) {
                Map<String, Object> tool_input = Json.fromJson(PATTERN2.matcher(content).group(1), Map.class);
                return new ToolResponse(matcher1.group(1), tool_input);
            }
            return null;
        }
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
                .id(UUID.randomUUID().toString())
                .name(toolSpecification.name())
                .arguments(Json.toJson(toolResponse.tool_input))
                .build();
    }
}
