package io.quarkiverse.langchain4j.gpullama3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.IntConsumer;

import org.beehive.gpullama3.auxiliary.LastRunMetrics;
import org.beehive.gpullama3.inference.sampler.Sampler;
import org.beehive.gpullama3.model.Model;
import org.beehive.gpullama3.model.format.ChatFormat;
import org.beehive.gpullama3.model.format.ToolCallExtract;
import org.jboss.logging.Logger;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.internal.Json;
import dev.langchain4j.internal.JsonSchemaElementUtils;
import dev.langchain4j.model.chat.request.ChatRequest;

abstract class GPULlama3BaseModel {

    private static final Logger LOG = Logger.getLogger(GPULlama3BaseModel.class);

    /**
     * Centralized holder of the actual model instance.
     * *Shared* across ChatModel and StreamingChatModel instances.
     * Lazily initialized by ensureInitialized() when first doChat() is called.
     */
    GPULlama3ModelHolder holder;

    public Model getModel() {
        return holder.model;
    }

    public Sampler getSampler() {
        return holder.sampler;
    }

    /**
     * Runs inference for the given {@link ChatRequest} and returns the raw decoded response text.
     *
     * When the request contains tool specifications, the tool definitions are injected into the
     * system message and tool-aware stop tokens are used so the model can signal a tool call.
     * The caller ({@link GPULlama3ChatModel} / {@link GPULlama3StreamingChatModel}) is responsible
     * for inspecting the raw text and deciding whether to return a tool execution request or a
     * plain text response.
     */
    public String modelResponse(ChatRequest request, IntConsumer tokenConsumer) {
        List<Integer> promptTokens = new ArrayList<>();

        if (holder.model.shouldAddBeginOfText()) {
            promptTokens.add(holder.chatFormat.getBeginOfText());
        }

        // Build tools JSON if the request carries tool definitions
        List<ToolSpecification> tools = request.toolSpecifications();
        String toolsJson = (tools != null && !tools.isEmpty()) ? buildToolsJson(tools) : null;

        processPromptMessages(request.messages(), promptTokens, toolsJson);

        // Tool-aware stop tokens only for the first generation (tool call turn).
        // On the synthesis turn (messages contain a prior tool result) use regular stops,
        // matching ToolCallingSession which switches to getStopTokens() for the final answer.
        boolean hasPriorToolResult = request.messages().stream()
                .anyMatch(m -> m.type() == dev.langchain4j.data.message.ChatMessageType.TOOL_EXECUTION_RESULT);
        Set<Integer> stopTokens = (toolsJson != null && !hasPriorToolResult)
                ? holder.chatFormat.getToolAwareStopTokens()
                : holder.chatFormat.getStopTokens();

        List<Integer> responseTokens;

        if (holder.onGPU) {
            responseTokens = holder.model.generateTokensGPU(
                    holder.state,
                    0,
                    promptTokens.subList(0, promptTokens.size()),
                    stopTokens,
                    holder.maxTokens,
                    holder.sampler,
                    false,
                    tokenConsumer,
                    holder.tornadoVMPlan);
        } else {
            responseTokens = holder.model.generateTokens(
                    holder.state,
                    0,
                    promptTokens.subList(0, promptTokens.size()),
                    stopTokens,
                    holder.maxTokens,
                    holder.sampler,
                    false,
                    tokenConsumer);
        }

        Integer stopToken = null;
        if (!responseTokens.isEmpty() && stopTokens.contains(responseTokens.getLast())) {
            stopToken = responseTokens.getLast();
            responseTokens.removeLast();
        }

        String responseText = holder.model.tokenizer().decode(responseTokens);

        // Append to conversation history
        promptTokens.addAll(responseTokens);

        // Add the stop token to complete the message
        if (stopToken != null) {
            promptTokens.add(stopToken);
        }

        if (stopToken == null) {
            return "Ran out of context length...\n Increase context length with by passing to llama-tornado --max-tokens XXX";
        } else {
            return responseText;
        }
    }
    // @formatter:on

    public void printLastMetrics() {
        LastRunMetrics.printMetrics();
    }

    /**
     * Encodes all conversation messages into {@code promptTokens}.
     *
     * Handles: UserMessage, SystemMessage (with optional tool-definition suffix),
     * AiMessage (plain text and with tool execution requests), ToolExecutionResultMessage.
     *
     * @param messageList messages in conversation order
     * @param toolsJson OpenAI-format tools JSON array, or {@code null} when no tools are registered
     */
    private void processPromptMessages(List<ChatMessage> messageList, List<Integer> promptTokens, String toolsJson) {
        boolean injectedTools = false;

        for (ChatMessage msg : messageList) {
            switch (msg.type()) {
                case USER -> {
                    UserMessage userMessage = (UserMessage) msg;
                    promptTokens.addAll(holder.chatFormat.encodeMessage(
                            new ChatFormat.Message(ChatFormat.Role.USER, userMessage.singleText())));
                }
                case SYSTEM -> {
                    SystemMessage systemMessage = (SystemMessage) msg;
                    if (holder.model.shouldAddSystemPrompt()) {
                        String content = systemMessage.text();
                        if (toolsJson != null && !injectedTools) {
                            content = content + holder.chatFormat.toolSystemPromptSuffix(toolsJson);
                            injectedTools = true;
                        }
                        promptTokens.addAll(
                                holder.chatFormat.encodeMessage(new ChatFormat.Message(ChatFormat.Role.SYSTEM, content)));
                    }
                }
                case AI -> {
                    AiMessage aiMessage = (AiMessage) msg;
                    if (aiMessage.hasToolExecutionRequests()) {
                        for (ToolExecutionRequest req : aiMessage.toolExecutionRequests()) {
                            promptTokens.addAll(holder.chatFormat.encodeToolCallAssistantTurn(
                                    new ToolCallExtract(req.name(), req.arguments())));
                        }
                    } else if (aiMessage.text() != null) {
                        promptTokens.addAll(
                                holder.chatFormat.encodeMessage(
                                        new ChatFormat.Message(ChatFormat.Role.ASSISTANT, aiMessage.text())));
                    }
                }
                case TOOL_EXECUTION_RESULT -> {
                    ToolExecutionResultMessage toolMessage = (ToolExecutionResultMessage) msg;
                    // LangChain4j serializes String tool results via Json.toJson(), producing a
                    // JSON-quoted string with escaped newlines. Unwrap it to plain readable text.
                    String resultText = unwrapToolResult(toolMessage.text());
                    LOG.infof("[Tool result] ← %s: %s", toolMessage.toolName(),
                            resultText.length() > 120 ? resultText.substring(0, 120).replace("\n", " ") + "…"
                                    : resultText.replace("\n", " "));
                    String wrapped = "Tool '" + toolMessage.toolName() + "' returned:\n" + resultText;
                    promptTokens.addAll(holder.chatFormat.encodeToolResultTurn(
                            toolMessage.id(), toolMessage.toolName(), wrapped));
                }
                default -> {
                    // Unsupported message types are silently skipped to avoid breaking existing flows
                }
            }
        }

        // If tools were requested but no system message was present, inject a tool-only system message
        if (toolsJson != null && !injectedTools && holder.model.shouldAddSystemPrompt()) {
            String toolsOnlySystem = holder.chatFormat.toolSystemPromptSuffix(toolsJson).stripLeading();
            promptTokens.addAll(0,
                    holder.chatFormat.encodeMessage(new ChatFormat.Message(ChatFormat.Role.SYSTEM, toolsOnlySystem)));
        }

        // After tool results, inject a synthesis instruction so the model answers from the result
        if (!messageList.isEmpty()
                && messageList.getLast().type() == dev.langchain4j.data.message.ChatMessageType.TOOL_EXECUTION_RESULT) {
            promptTokens.addAll(holder.chatFormat.encodeMessage(new ChatFormat.Message(ChatFormat.Role.USER,
                    "Using only the tool result above, answer the user's original question.")));
        }

        // Prime the model to start generating an assistant response
        promptTokens.addAll(holder.chatFormat.encodeHeader(new ChatFormat.Message(ChatFormat.Role.ASSISTANT, "")));
    }

    /**
     * Converts a LangChain4j {@link ToolSpecification} list to the pretty-printed JSON array
     * expected by both {@code LlamaChatFormat} and {@code Qwen3ChatFormat} tool system prompts.
     *
     * Uses the same indented format as {@code ToolRegistry.toToolsJson()} so that small models
     * (1B/3B) can cleanly distinguish the outer tool envelope from the nested parameters schema
     * and do not confuse the schema definition with the arguments they must fill in.
     */
    static String buildToolsJson(List<ToolSpecification> tools) {
        StringBuilder sb = new StringBuilder("[\n");
        for (int i = 0; i < tools.size(); i++) {
            ToolSpecification tool = tools.get(i);
            sb.append("  {\n");
            sb.append("    \"name\": \"").append(escapeJson(tool.name())).append("\",\n");
            if (tool.description() != null) {
                sb.append("    \"description\": \"").append(escapeJson(tool.description())).append("\",\n");
            }
            sb.append("    \"parameters\": ").append(buildParametersJson(tool)).append("\n");
            sb.append("  }");
            if (i < tools.size() - 1)
                sb.append(",");
            sb.append("\n");
        }
        sb.append("]");
        return sb.toString();
    }

    private static String buildParametersJson(ToolSpecification tool) {
        if (tool.parameters() == null) {
            return "{\"type\":\"object\",\"properties\":{}}";
        }
        var params = new LinkedHashMap<String, Object>();
        params.put("type", "object");
        var props = new LinkedHashMap<String, Object>();
        for (var entry : tool.parameters().properties().entrySet()) {
            props.put(entry.getKey(), JsonSchemaElementUtils.toMap(entry.getValue()));
        }
        params.put("properties", props);
        List<String> required = tool.parameters().required();
        if (required != null && !required.isEmpty()) {
            params.put("required", required);
        }
        return Json.toJson(params);
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * LangChain4j serializes {@code String} tool results via {@code Json.toJson()}, which wraps
     * them in a JSON string literal: {@code "hello\nworld"} becomes {@code "\"hello\\nworld\""}.
     * Unwrap the JSON quoting so the model receives plain readable text with real newlines.
     */
    private static String unwrapToolResult(String text) {
        if (text == null)
            return "";
        if (text.startsWith("\"")) {
            try {
                return Json.fromJson(text, String.class);
            } catch (Exception ignored) {
            }
        }
        return text;
    }
}
