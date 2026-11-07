package io.quarkiverse.langchain4j.gpullama3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;

import org.beehive.gpullama3.inference.sampler.Sampler;
import org.beehive.gpullama3.model.Model;
import org.beehive.gpullama3.model.format.ChatFormat;
import org.beehive.gpullama3.model.format.ToolCallExtract;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

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

        if (toolsJson != null && !holder.chatFormat.supportsToolCalling()) {
            throw new UnsupportedOperationException(
                    "Tool calling is not supported for model format: " + holder.chatFormat.getClass().getSimpleName());
        }

        processPromptMessages(request.messages(), promptTokens, toolsJson);

        if (toolsJson != null) {
            boolean hasPriorToolResult = request.messages().stream()
                    .anyMatch(m -> m.type() == dev.langchain4j.data.message.ChatMessageType.TOOL_EXECUTION_RESULT);
            String toolNames = tools.stream()
                    .map(ToolSpecification::name)
                    .collect(Collectors.joining(", "));
            long priorResultCount = request.messages().stream()
                    .filter(m -> m.type() == dev.langchain4j.data.message.ChatMessageType.TOOL_EXECUTION_RESULT)
                    .count();
            if (hasPriorToolResult) {
                LOG.infof("[Tool turn] %d tool(s) available: %s  (after %d result(s))",
                        tools.size(), toolNames, priorResultCount);
            } else {
                LOG.infof("[Tool turn] %d tool(s) available: %s", tools.size(), toolNames);
            }
        }

        // Full decoded prompt the model is about to see this turn: includes the thinking-control
        // primer, injected tool definitions, re-encoded prior assistant tool-call turns, and the
        // <tool_response>-wrapped tool results. This is the precise view for debugging tool calls
        // and tool responses. Enable with:
        //   quarkus.log.category."io.quarkiverse.langchain4j.gpullama3".level=DEBUG
        if (LOG.isDebugEnabled()) {
            LOG.debugf("[Prompt: %d tokens]%n>>>%n%s%n<<<",
                    promptTokens.size(), holder.model.tokenizer().decode(promptTokens));
        }

        // Use tool-aware stop tokens whenever tools are present so the model can signal a tool
        // call (eom_id on LLaMA 3.1) or a regular response (eot_id) on every turn — including
        // turns that already contain prior tool results.
        Set<Integer> stopTokens = (toolsJson != null)
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

        LOG.debug("stopToken=" + stopToken + "  responseTokens=" + responseTokens.size()
                + "  raw response: >>>" + responseText + "<<<");

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

        // Tool definitions are injected on every turn so the model always knows which tools
        // are available — matching Ollama's behaviour of sending the tools array with every
        // request. Models correctly decide when to call a tool vs. synthesise a final answer
        // based on whether they already have enough information from prior tool results.
        boolean hasPriorToolResult = messageList.stream()
                .anyMatch(m -> m.type() == dev.langchain4j.data.message.ChatMessageType.TOOL_EXECUTION_RESULT);
        boolean injectTools = toolsJson != null;
        boolean userMessageInjection = injectTools && holder.chatFormat.injectsToolsInUserMessage();

        LOG.debug("processPromptMessages: msgs=" + messageList.size()
                + "  injectTools=" + injectTools + "  userMsgInjection=" + userMessageInjection);

        for (ChatMessage msg : messageList) {
            switch (msg.type()) {
                case SYSTEM -> {
                    SystemMessage systemMessage = (SystemMessage) msg;
                    if (holder.model.shouldAddSystemPrompt()) {
                        String content = systemMessage.text();
                        if (injectTools) {
                            if (userMessageInjection) {
                                String prefix = holder.chatFormat.toolSystemMessagePrefix();
                                if (!prefix.isEmpty())
                                    content = prefix + content;
                            } else {
                                content = content + holder.chatFormat.toolSystemPromptSuffix(toolsJson);
                                toolsInjected = true;
                            }
                        }
                        LOG.debugf("SYSTEM (first 300): %s",
                                content.substring(0, Math.min(300, content.length())));
                        promptTokens.addAll(
                                holder.chatFormat.encodeMessage(new ChatFormat.Message(ChatFormat.Role.SYSTEM, content)));
                    }
                }
                case USER -> {
                    UserMessage userMessage = (UserMessage) msg;
                    String userText = userMessage.singleText();
                    if (injectTools && !toolsInjected && userMessageInjection) {
                        userText = holder.chatFormat.toolFirstUserMessagePrefix(toolsJson) + userText;
                        toolsInjected = true;
                    }
                    LOG.debugf("USER (first 200): %s", userText.substring(0, Math.min(200, userText.length())));
                    promptTokens.addAll(holder.chatFormat.encodeMessage(
                            new ChatFormat.Message(ChatFormat.Role.USER, userText)));
                }
                case AI -> {
                    AiMessage aiMessage = (AiMessage) msg;
                    if (aiMessage.hasToolExecutionRequests()) {
                        List<ToolCallExtract> toolCalls = aiMessage.toolExecutionRequests().stream()
                                .map(req -> new ToolCallExtract(req.name(), req.arguments()))
                                .collect(Collectors.toList());
                        promptTokens.addAll(holder.chatFormat.encodeToolCallAssistantTurn(toolCalls));
                    } else if (aiMessage.text() != null) {
                        promptTokens.addAll(
                                holder.chatFormat.encodeMessage(
                                        new ChatFormat.Message(ChatFormat.Role.ASSISTANT, aiMessage.text())));
                    }
                }
                case TOOL_EXECUTION_RESULT -> {
                    ToolExecutionResultMessage toolMessage = (ToolExecutionResultMessage) msg;
                    String resultText = unwrapToolResult(toolMessage.text());
                    LOG.infof("[Tool result] ← %s:\n%s", toolMessage.toolName(), resultText);
                    promptTokens.addAll(holder.chatFormat.encodeToolResultTurn(
                            toolMessage.id(), toolMessage.toolName(), resultText));
                }
                default -> {
                    // Unsupported message types are silently skipped
                }
            }
        }

        // Fallback: no system or user message encountered — inject tools at the start
        if (injectTools && !toolsInjected && !userMessageInjection && holder.model.shouldAddSystemPrompt()) {
            String toolsOnlySystem = holder.chatFormat.toolSystemPromptSuffix(toolsJson).stripLeading();
            promptTokens.addAll(0,
                    holder.chatFormat.encodeMessage(new ChatFormat.Message(ChatFormat.Role.SYSTEM, toolsOnlySystem)));
        }

        // Prime the model to start generating an assistant response
        promptTokens.addAll(holder.chatFormat.encodeHeader(new ChatFormat.Message(ChatFormat.Role.ASSISTANT, "")));

        // Control the reasoning phase: when thinking is disabled, formats that support it
        // (e.g. Qwen3) prime a pre-closed thinking block so the model skips reasoning. No-op
        // for formats without a thinking mode.
        promptTokens.addAll(holder.chatFormat.encodeThinkingControl(holder.enableThinking));
    }

    private static final String CALL_ID_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";

    /** Generates an Ollama-style tool call ID: {@code call_} + 8 random alphanumeric chars. */
    protected static String generateCallId() {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        StringBuilder sb = new StringBuilder("call_");
        for (int i = 0; i < 8; i++) {
            sb.append(CALL_ID_CHARS.charAt(rng.nextInt(CALL_ID_CHARS.length())));
        }
        return sb.toString();
    }

    private static final ObjectMapper TOOL_MAPPER = new ObjectMapper();
    /** Compact single-line JSON writer — Qwen3 ChatML tool list. */
    private static final ObjectWriter COMPACT_TOOL_WRITER = TOOL_MAPPER.writer();
    /** 4-space pretty JSON writer — Llama Instruct tool list (matches the template's {@code tojson(indent=4)}). */
    private static final ObjectWriter PRETTY_TOOL_WRITER = TOOL_MAPPER.writer(
            new DefaultPrettyPrinter()
                    .withObjectIndenter(new DefaultIndenter("    ", "\n"))
                    .withArrayIndenter(new DefaultIndenter("    ", "\n")));

    /**
     * Builds the {@code <tools>} JSON for the model family, dispatching on
     * {@link Model#getModelType()}. Each family expects a different serialization (see the
     * dedicated builders). Only Llama and Qwen3/ChatML formats currently reach this method —
     * tool calling is gated upstream by {@code chatFormat.supportsToolCalling()}.
     */
    private String buildToolsJson(List<ToolSpecification> tools) {
        return switch (holder.model.getModelType()) {
            case LLAMA_3 -> buildToolsJsonLlama(tools);
            case QWEN_3, DEEPSEEK_R1_DISTILL_QWEN -> buildToolsJsonQwen3(tools);
            // Llama 3.1 and 3.2 both report LLAMA_3 and share the same tool template, so no
            // per-version split is needed. Any other ChatML-based format that opts into tool
            // calling later falls back to the Qwen3 layout.
            default -> buildToolsJsonQwen3(tools);
        };
    }

    /**
     * Qwen3 tool list: each tool object as <em>compact</em> single-line JSON, one per line, with
     * no enclosing array — matching the official Qwen3 chat template, which renders the section as
     * {@code <tools>\n{tool|tojson}\n…\n</tools>}. Compact output keeps the per-turn token cost low.
     */
    static String buildToolsJsonQwen3(List<ToolSpecification> tools) {
        return buildToolMaps(tools).stream()
                .map(m -> writeJson(COMPACT_TOOL_WRITER, m))
                .collect(Collectors.joining("\n"));
    }

    /**
     * Llama 3.1/3.2 tool list: each tool object <em>pretty-printed with 4-space indentation</em>,
     * separated by blank lines, with no enclosing array — matching the Llama Instruct template,
     * which renders each tool via {@code t | tojson(indent=4)} in the first user message.
     */
    static String buildToolsJsonLlama(List<ToolSpecification> tools) {
        return buildToolMaps(tools).stream()
                .map(m -> writeJson(PRETTY_TOOL_WRITER, m))
                .collect(Collectors.joining("\n\n"));
    }

    /** Builds the shared OpenAI-style tool maps ({@code {"type":"function","function":{…}}}). */
    private static List<Map<String, Object>> buildToolMaps(List<ToolSpecification> tools) {
        List<Map<String, Object>> toolArray = new ArrayList<>();
        for (ToolSpecification tool : tools) {
            Map<String, Object> funcMap = new LinkedHashMap<>();
            funcMap.put("name", tool.name());
            if (tool.description() != null) {
                funcMap.put("description", tool.description());
            }
            funcMap.put("parameters", buildParametersMap(tool));

            Map<String, Object> toolMap = new LinkedHashMap<>();
            toolMap.put("type", "function");
            toolMap.put("function", funcMap);
            toolArray.add(toolMap);
        }
        return toolArray;
    }

    private static String writeJson(ObjectWriter writer, Object value) {
        try {
            return writer.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize tool definition JSON", e);
        }
    }

    private static Map<String, Object> buildParametersMap(ToolSpecification tool) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();
        if (tool.parameters() != null) {
            for (var entry : tool.parameters().properties().entrySet()) {
                props.put(entry.getKey(), JsonSchemaElementUtils.toMap(entry.getValue()));
            }
            List<String> required = tool.parameters().required();
            if (required != null && !required.isEmpty()) {
                params.put("required", required);
            }
        }
        params.put("properties", props);
        return params;
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
