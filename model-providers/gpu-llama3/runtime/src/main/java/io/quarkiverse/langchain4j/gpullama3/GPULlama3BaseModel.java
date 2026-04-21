package io.quarkiverse.langchain4j.gpullama3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.IntConsumer;

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
        System.err.println("[GPU-DEBUG] hasPriorToolResult=" + hasPriorToolResult + "  stopTokens=" + stopTokens);

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

        System.err.println("[GPU-DEBUG] stopToken=" + stopToken + "  responseTokens=" + responseTokens.size());
        System.err.println("[GPU-DEBUG] raw response: >>>" + responseText + "<<<");

        if (stopToken == null) {
            return "Ran out of context length...\n Increase context length with by passing to llama-tornado --max-tokens XXX";
        } else {
            return responseText;
        }
    }
    // @formatter:on

    /**
     * Encodes all conversation messages into {@code promptTokens} using the <b>native Llama 3.2
     * chat template</b> extracted from the GGUF metadata.
     *
     * <p>
     * Key template behaviours reproduced here:
     * <ul>
     * <li>System message gets an {@code "Environment: ipython\n"} prefix when tools are active.</li>
     * <li>Tool definitions are injected into the <em>first</em> user message
     * ({@code tools_in_user_message = true} is the default in the template).</li>
     * <li>Assistant tool-call turns are encoded as native JSON:
     * {@code {"name":"…","parameters":{…}}} — not {@code <tool_call>} XML.</li>
     * <li>Tool results use the {@code ipython} role (handled by
     * {@link ChatFormat#encodeToolResultTurn}).</li>
     * </ul>
     */
    private void processPromptMessages(List<ChatMessage> messageList, List<Integer> promptTokens, String toolsJson) {
        boolean toolsInjected = false;
        boolean userMessageInjection = toolsJson != null && holder.chatFormat.injectsToolsInUserMessage();

        System.err.println("\n[GPU-DEBUG] ── processPromptMessages  msgs=" + messageList.size()
                + "  toolsJson=" + (toolsJson != null)
                + "  userMsgInjection=" + userMessageInjection + " ──────────────");

        for (ChatMessage msg : messageList) {
            switch (msg.type()) {
                case SYSTEM -> {
                    SystemMessage systemMessage = (SystemMessage) msg;
                    if (holder.model.shouldAddSystemPrompt()) {
                        String content = systemMessage.text();
                        if (toolsJson != null) {
                            if (userMessageInjection) {
                                String prefix = holder.chatFormat.toolSystemMessagePrefix();
                                if (!prefix.isEmpty())
                                    content = prefix + content;
                            } else {
                                content = content + holder.chatFormat.toolSystemPromptSuffix(toolsJson);
                                toolsInjected = true;
                            }
                        }
                        System.err.println("[GPU-DEBUG] SYSTEM (first 300): "
                                + content.substring(0, Math.min(300, content.length())));
                        promptTokens.addAll(
                                holder.chatFormat.encodeMessage(new ChatFormat.Message(ChatFormat.Role.SYSTEM, content)));
                    }
                }
                case USER -> {
                    UserMessage userMessage = (UserMessage) msg;
                    String userText = userMessage.singleText();
                    if (toolsJson != null && !toolsInjected && userMessageInjection) {
                        userText = holder.chatFormat.toolFirstUserMessagePrefix(toolsJson) + userText;
                        toolsInjected = true;
                    }
                    System.err.println("[GPU-DEBUG] USER (first 200): "
                            + userText.substring(0, Math.min(200, userText.length())));
                    promptTokens.addAll(holder.chatFormat.encodeMessage(
                            new ChatFormat.Message(ChatFormat.Role.USER, userText)));
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
                    String resultText = unwrapToolResult(toolMessage.text());
                    LOG.infof("[Tool result] ← %s: %s", toolMessage.toolName(),
                            resultText.length() > 120 ? resultText.substring(0, 120).replace("\n", " ") + "…"
                                    : resultText.replace("\n", " "));
                    promptTokens.addAll(holder.chatFormat.encodeToolResultTurn(
                            toolMessage.id(), toolMessage.toolName(), resultText));
                }
                default -> {
                    // Unsupported message types are silently skipped
                }
            }
        }

        // Fallback: no system or user message encountered — inject tools at the start
        if (toolsJson != null && !toolsInjected && !userMessageInjection && holder.model.shouldAddSystemPrompt()) {
            String toolsOnlySystem = holder.chatFormat.toolSystemPromptSuffix(toolsJson).stripLeading();
            promptTokens.addAll(0,
                    holder.chatFormat.encodeMessage(new ChatFormat.Message(ChatFormat.Role.SYSTEM, toolsOnlySystem)));
        }

        // After a tool result, small models tend to echo the raw data instead of synthesising.
        // An explicit user instruction breaks the JSON-continuation pattern.
        boolean lastIsTool = !messageList.isEmpty()
                && messageList.getLast().type() == dev.langchain4j.data.message.ChatMessageType.TOOL_EXECUTION_RESULT;
        if (lastIsTool) {
            promptTokens.addAll(holder.chatFormat.encodeMessage(new ChatFormat.Message(ChatFormat.Role.USER,
                    "Answer the user's original question in natural language using the tool result above. Be concise.")));
        }

        // Prime the model to start generating an assistant response
        promptTokens.addAll(holder.chatFormat.encodeHeader(new ChatFormat.Message(ChatFormat.Role.ASSISTANT, "")));
    }

    /**
     * @deprecated unused after moving tool injection into chatFormat.toolFirstUserMessagePrefix
     */
    @Deprecated(forRemoval = true)
    private static String buildIndividualToolsJson(String openAiArrayJson) {
        // openAiArrayJson is already built by buildToolsJson as a pretty array.
        // Strip the outer [ ] and split on top-level object boundaries to get individual objects.
        // Simpler: just strip the enclosing brackets and the leading/trailing whitespace.
        String stripped = openAiArrayJson.strip();
        if (stripped.startsWith("["))
            stripped = stripped.substring(1);
        if (stripped.endsWith("]"))
            stripped = stripped.substring(0, stripped.length() - 1);
        // Remove leading/trailing commas+whitespace between objects (the array separator)
        // Each object starts with "  {" — trim to just the objects
        return stripped.strip();
    }

    /**
     * Builds the tools JSON array in OpenAI / native Llama 3.2 format:
     * {@code [{"type":"function","function":{"name":...,"description":...,"parameters":{...}}}]}.
     *
     * This matches the format Ollama sends to the model and the format Llama 3.2 expects in its
     * native {@code <|start_header_id|>tools<|end_header_id|>} section. Using this format (rather
     * than flat JSON without the {@code type/function} wrapper) triggers reliable tool calling.
     */
    static String buildToolsJson(List<ToolSpecification> tools) {
        StringBuilder sb = new StringBuilder("[\n");
        for (int i = 0; i < tools.size(); i++) {
            ToolSpecification tool = tools.get(i);
            sb.append("  {\n");
            sb.append("    \"type\": \"function\",\n");
            sb.append("    \"function\": {\n");
            sb.append("      \"name\": \"").append(escapeJson(tool.name())).append("\",\n");
            if (tool.description() != null) {
                sb.append("      \"description\": \"").append(escapeJson(tool.description())).append("\",\n");
            }
            sb.append("      \"parameters\": ").append(buildParametersJson(tool)).append("\n");
            sb.append("    }\n");
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
