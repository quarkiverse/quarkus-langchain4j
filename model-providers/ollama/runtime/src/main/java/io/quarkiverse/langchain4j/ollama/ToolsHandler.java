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

public class ToolsHandler {

    private static final Logger log = Logger.getLogger(ToolsHandler.class);

    private static final PromptTemplate DEFAULT_SYSTEM_TEMPLATE = PromptTemplate
            .from("""
                    You are a helpful AI assistant responding to user requests delimited by ---.
                    You only have access to the following tools:
                    {tools}

                    Select the most appropriate tools from the list bellow and only from this list, and respond with a JSON object containing:
                        - "tools" containing a list of selected tools in JSON Format containing:
                            - "name": < selected tool name >
                            - "inputs": < required parameters matching exactly the tool's JSON schema >
                            - "result_id": < an id to identify the result of this tool, ex: id1 >
                        - "response" : < answer or description in text format using tools result_id>

                    Follow these guidelines:
                        - The tools "inputs" and "response" can reference previous tools result by using this format: $(xxx) xxx being a previous result_id
                        - Break down complex requests into sequential and mandatory tools from the provided list of tools.
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
        // Test if it's an AI Service request with tool results
        boolean hasResultMessages = messages.stream().anyMatch(m -> m.role() == Role.TOOL_EXECUTION_RESULT);
        if (hasResultMessages) {
            String result = messages.stream().filter(term -> term.role() == Role.ASSISTANT)
                    .map(Message::content).collect(Collectors.joining("\n"));
            return Response.from(AiMessage.from(result));
        }

        builder.format("json");
        Message groupedSystemMessage = createSystemMessageWithTools(messages, toolSpecifications);

        List<Message> otherMessages = messages.stream().filter(cm -> cm.role() != Role.SYSTEM).toList();
        Message initialUserMessage = Message.builder()
                .role(Role.USER)
                .content("--- " + otherMessages.get(0).content() + " ---").build();

        List<Message> messagesWithTools = new ArrayList<>(messages.size() + 1);
        messagesWithTools.add(groupedSystemMessage);
        messagesWithTools.addAll(otherMessages.subList(1, otherMessages.size()));
        messagesWithTools.add(initialUserMessage);

        builder.messages(messagesWithTools);

        ChatResponse response = client.chat(builder.build());
        AiMessage aiMessage = handleResponse(response, toolSpecifications);
        return Response.from(aiMessage, new TokenUsage(response.promptEvalCount(), response.evalCount()));
    }

    private Message createSystemMessageWithTools(List<Message> messages, List<ToolSpecification> toolSpecifications) {
        Prompt prompt = DEFAULT_SYSTEM_TEMPLATE.apply(
                Map.of("tools", Json.toJson(toolSpecifications)));

        String initialSystemMessages = messages.stream().filter(sm -> sm.role() == Role.SYSTEM)
                .map(Message::content)
                .collect(Collectors.joining("\n"));

        return Message.builder()
                .role(Role.SYSTEM)
                .content(prompt.text() + "\n" + initialSystemMessages)
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

        if (toolResponses.response != null) {
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
