package io.quarkiverse.langchain4j.ollama;

import static io.quarkiverse.langchain4j.ollama.ChatRequest.Builder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.internal.Json;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import io.quarkus.runtime.annotations.RegisterForReflection;

public class ExperimentalToolsChatLM {

    private static final Logger log = Logger.getLogger(ExperimentalToolsChatLM.class);

    private static final PromptTemplate DEFAULT_SYSTEM_TEMPLATE = PromptTemplate
            .from("""
                    --- Context ---
                    {context}
                    ---------------

                    You are a helpful AI assistant responding to user requests taking into account the previous context.
                    You have access to the following tools:
                    {tools}

                    Create a list of most appropriate tools to call in order to answer to the user request.
                    If no tools are required respond with response field directly.
                    Respond with a JSON object containing required "tools" and required not null "response" fields:
                        - "tools": a list of selected tools in JSON format, each with:
                            - "name": <selected tool name>
                            - "inputs": <required parameters using tools result_id matching the tool's JSON schema>
                            - "result_id": <an id to identify the result of this tool, e.g., id1>
                        - "response": < Summary of tools used with your response using tools result_id>

                    Guidelines:
                        - Only reference previous tools results using the format: $(xxx), where xxx is a previous result_id.
                        - Break down complex requests into sequential and necessary tools.
                        - Use previous results through result_id for inputs response, do not invent them.
                    """);

    @RegisterForReflection
    record ToolResponses(List<ToolResponse> tools, String response) {
    }

    @RegisterForReflection
    record ToolResponse(String name, Map<String, Object> inputs, String result_id) {
    }

    public Response<AiMessage> chat(OllamaClient client, Builder builder, List<Message> messages,
            List<ToolSpecification> toolSpecifications,
            ToolSpecification toolThatMustBeExecuted) {
        // Test if it's an AI request with tools execution response.
        boolean hasResultMessages = messages.stream().anyMatch(m -> m.role() == Role.TOOL_EXECUTION_RESULT);
        if (hasResultMessages) {
            String result = messages.stream().filter(term -> term.role() == Role.ASSISTANT)
                    .map(Message::content).collect(Collectors.joining("\n"));
            return Response.from(AiMessage.from(result));
        }
        // Creates Chat request
        builder.format("json");
        Message systemMessage = createSystemMessageWithTools(messages, toolSpecifications);

        List<Message> otherMessages = messages.stream().filter(cm -> cm.role() != Role.SYSTEM).toList();
        List<Message> messagesWithTools = new ArrayList<>(otherMessages.size() + 1);
        messagesWithTools.add(systemMessage);
        messagesWithTools.addAll(otherMessages);

        builder.messages(messagesWithTools);

        ChatResponse response = client.chat(builder.build());
        AiMessage aiMessage = handleResponse(response, toolSpecifications);
        return Response.from(aiMessage, new TokenUsage(response.promptEvalCount(), response.evalCount()));
    }

    private Message createSystemMessageWithTools(List<Message> messages, List<ToolSpecification> toolSpecifications) {
        String initialSystemMessages = messages.stream().filter(sm -> sm.role() == Role.SYSTEM)
                .map(Message::content)
                .collect(Collectors.joining("\n"));
        Prompt prompt = DEFAULT_SYSTEM_TEMPLATE.apply(
                Map.of("tools", Json.toJson(toolSpecifications),
                        "context", initialSystemMessages));
        return Message.builder()
                .role(Role.SYSTEM)
                .content(prompt.text())
                .build();
    }

    private AiMessage handleResponse(ChatResponse response, List<ToolSpecification> toolSpecifications) {
        ToolResponses toolResponses;
        try {
            toolResponses = Json.fromJson(response.message().content(), ToolResponses.class);
        } catch (Exception e) {
            throw new RuntimeException("Ollama server did not respond with valid JSON. Please try again!");
        }

        if (toolResponses.response != null && (toolResponses.tools == null || toolResponses.tools.isEmpty())) {
            return AiMessage.from(toolResponses.response);
        }

        List<ToolExecutionRequest> toolExecutionRequests = new ArrayList<>();
        List<String> availableTools = toolSpecifications.stream().map(ToolSpecification::name).toList();

        for (ToolResponse toolResponse : toolResponses.tools) {
            if (!availableTools.contains(toolResponse.name)) {
                throw new RuntimeException(String.format(
                        "Ollama server wants to call a name '%s' that is not part of the available tools %s",
                        toolResponse.name, availableTools));
            } else {
                getToolSpecification(toolResponse, toolSpecifications)
                        .map(ts -> toToolExecutionRequest(toolResponse, ts))
                        .ifPresent(toolExecutionRequests::add);
            }
        }

        if (toolResponses.response != null && !toolResponses.response().isEmpty()) {
            return new AiMessage(toolResponses.response, toolExecutionRequests);
        }
        return AiMessage.from(toolExecutionRequests);
    }

    private Optional<ToolSpecification> getToolSpecification(ToolResponse toolResponse,
            List<ToolSpecification> toolSpecifications) {
        return toolSpecifications.stream()
                .filter(ts -> ts.name().equals(toolResponse.name))
                .findFirst();
    }

    private static ToolExecutionRequest toToolExecutionRequest(
            ToolResponse toolResponse, ToolSpecification toolSpecification) {
        return ToolExecutionRequest.builder()
                .id(toolResponse.result_id)
                .name(toolSpecification.name())
                .arguments(Json.toJson(toolResponse.inputs))
                .build();
    }
}