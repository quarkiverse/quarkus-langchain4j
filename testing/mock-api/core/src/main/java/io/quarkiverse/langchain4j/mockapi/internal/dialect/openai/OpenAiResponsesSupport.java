package io.quarkiverse.langchain4j.mockapi.internal.dialect.openai;

import static io.quarkiverse.langchain4j.mockapi.internal.dialect.Json.text;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.ChatRequestView;
import io.quarkiverse.langchain4j.mockapi.SseEvent;
import io.quarkiverse.langchain4j.mockapi.ToolCall;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Chunker;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Json;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Rendered;
import io.quarkiverse.langchain4j.mockapi.internal.json.MockApiJson;
import io.quarkiverse.langchain4j.mockapi.internal.model.ChatRequestViewImpl;
import io.quarkiverse.langchain4j.mockapi.internal.model.MessageImpl;
import io.quarkiverse.langchain4j.mockapi.internal.model.ToolDefinitionImpl;

/**
 * Request parsing and response rendering for the OpenAI Responses API ({@code /responses}).
 */
final class OpenAiResponsesSupport {

    private final ObjectMapper mapper = MockApiJson.mapper();

    ChatRequestView parse(JsonNode body) {
        List<ChatRequestView.Message> messages = new ArrayList<>();
        if (body.hasNonNull("instructions")) {
            messages.add(new MessageImpl("system", body.get("instructions").asText(), null, null, null));
        }
        JsonNode input = body.get("input");
        if (input != null && input.isTextual()) {
            messages.add(new MessageImpl("user", input.asText(), null, null, null));
        } else if (input != null && input.isArray()) {
            for (JsonNode item : input) {
                messages.add(parseItem(item));
            }
        }
        List<ChatRequestView.ToolDefinition> tools = new ArrayList<>();
        for (JsonNode tool : body.path("tools")) {
            if ("function".equals(tool.path("type").asText())) {
                tools.add(new ToolDefinitionImpl(text(tool, "name"),
                        text(tool, "description"),
                        tool.hasNonNull("parameters") ? tool.get("parameters") : null));
            }
        }
        JsonNode choice = body.get("tool_choice");
        String toolChoice = choice == null || choice.isNull() ? null
                : choice.isTextual() ? choice.asText() : text(choice, "name");
        return new ChatRequestViewImpl(
                text(body, "model"),
                body.path("stream").asBoolean(false),
                body.hasNonNull("temperature") ? body.get("temperature").asDouble() : null,
                body.hasNonNull("top_p") ? body.get("top_p").asDouble() : null,
                body.hasNonNull("max_output_tokens") ? body.get("max_output_tokens").asInt() : null,
                messages,
                tools,
                toolChoice,
                null,
                null);
    }

    private ChatRequestView.Message parseItem(JsonNode item) {
        String type = item.path("type").asText("message");
        if ("function_call".equals(type)) {
            return new MessageImpl("assistant", null,
                    List.of(ToolCall.of(text(item, "call_id"), text(item, "name"),
                            text(item, "arguments"))),
                    null, null);
        }
        if ("function_call_output".equals(type)) {
            return new MessageImpl("tool", text(item, "output"), null,
                    text(item, "call_id"), null);
        }
        return new MessageImpl(text(item, "role"),
                Json.contentText(item.get("content"), Set.of("input_text", "output_text")),
                null, null, null);
    }

    Rendered render(ChatReply reply, CapturedRequest req) {
        ArrayNode output = mapper.createArrayNode();
        output.addAll(items(reply, req));
        return Rendered.json(200, MockApiJson.toBytes(response(reply, req, "completed", output)));
    }

    Rendered renderStream(ChatReply reply, CapturedRequest req) {
        List<SseEvent> events = new ArrayList<>();
        events.add(event(events, "response.created",
                e -> e.set("response", response(reply, req, "in_progress", mapper.createArrayNode()))));
        events.add(event(events, "response.in_progress",
                e -> e.set("response", response(reply, req, "in_progress", mapper.createArrayNode()))));
        ArrayNode output = mapper.createArrayNode();
        List<ObjectNode> items = items(reply, req);
        for (int i = 0; i < items.size(); i++) {
            ObjectNode full = items.get(i);
            ObjectNode started = inProgress(full);
            int index = i;
            String itemId = full.path("id").asText();
            events.add(event(events, "response.output_item.added", e -> {
                e.put("output_index", index);
                e.set("item", started);
            }));
            switch (full.path("type").asText()) {
                case "message" -> streamMessage(events, reply, itemId, index, full);
                case "function_call" -> {
                    String args = full.path("arguments").asText();
                    events.add(event(events, "response.function_call_arguments.delta", e -> {
                        e.put("item_id", itemId);
                        e.put("output_index", index);
                        e.put("delta", args);
                    }));
                    events.add(event(events, "response.function_call_arguments.done", e -> {
                        e.put("item_id", itemId);
                        e.put("output_index", index);
                        e.put("arguments", args);
                    }));
                }
                default -> {
                    // reasoning items carry their full summary on the added/done events only
                }
            }
            events.add(event(events, "response.output_item.done", e -> {
                e.put("output_index", index);
                e.set("item", full);
            }));
            output.add(full);
        }
        events.add(event(events, "response.completed", e -> e.set("response", response(reply, req, "completed", output))));
        return Rendered.stream(Rendered.StreamFormat.SSE, events);
    }

    private void streamMessage(List<SseEvent> events, ChatReply reply, String itemId, int index, ObjectNode full) {
        ObjectNode emptyPart = outputText("");
        events.add(event(events, "response.content_part.added", e -> {
            e.put("item_id", itemId);
            e.put("output_index", index);
            e.put("content_index", 0);
            e.set("part", emptyPart);
        }));
        for (String chunk : Chunker.chunks(reply)) {
            events.add(event(events, "response.output_text.delta", e -> {
                e.put("item_id", itemId);
                e.put("output_index", index);
                e.put("content_index", 0);
                e.put("delta", chunk);
                e.putArray("logprobs");
            }));
        }
        String text = full.at("/content/0/text").asText();
        events.add(event(events, "response.output_text.done", e -> {
            e.put("item_id", itemId);
            e.put("output_index", index);
            e.put("content_index", 0);
            e.put("text", text);
            e.putArray("logprobs");
        }));
        events.add(event(events, "response.content_part.done", e -> {
            e.put("item_id", itemId);
            e.put("output_index", index);
            e.put("content_index", 0);
            e.set("part", full.at("/content/0"));
        }));
    }

    /**
     * Builds an event whose {@code sequence_number} is its position in the stream.
     */
    private SseEvent event(List<SseEvent> events, String type, Consumer<ObjectNode> fill) {
        ObjectNode e = mapper.createObjectNode();
        e.put("type", type);
        fill.accept(e);
        e.put("sequence_number", events.size());
        return SseEvent.of(type, e.toString());
    }

    /**
     * The complete output items in order: reasoning (if any), then the message (if any), then function calls.
     */
    private List<ObjectNode> items(ChatReply reply, CapturedRequest req) {
        List<ObjectNode> items = new ArrayList<>();
        if (reply.thinking() != null && !reply.thinking().isEmpty()) {
            ObjectNode item = mapper.createObjectNode();
            item.put("type", "reasoning");
            item.put("id", "rs_mock_" + req.index());
            item.putArray("summary").addObject().put("type", "summary_text").put("text", reply.thinking());
            item.put("status", "completed");
            items.add(item);
        }
        if (reply.text() != null || reply.toolCalls() == null || reply.toolCalls().isEmpty()) {
            ObjectNode item = mapper.createObjectNode();
            item.put("type", "message");
            item.put("id", "msg_mock_" + req.index());
            item.put("role", "assistant");
            item.putArray("content").add(outputText(reply.text() == null ? "" : reply.text()));
            item.put("status", "completed");
            items.add(item);
        }
        if (reply.toolCalls() != null) {
            for (int i = 0; i < reply.toolCalls().size(); i++) {
                ToolCall tc = reply.toolCalls().get(i);
                ObjectNode item = mapper.createObjectNode();
                item.put("type", "function_call");
                item.put("id", "fc_mock_" + req.index() + "_" + i);
                item.put("call_id", Json.callId(tc, i));
                item.put("name", tc.name());
                item.put("arguments", tc.argumentsJson());
                item.put("status", "completed");
                items.add(item);
            }
        }
        return items;
    }

    /**
     * The in-progress form of a complete item, as sent on {@code response.output_item.added}.
     */
    private static ObjectNode inProgress(ObjectNode complete) {
        ObjectNode item = complete.deepCopy();
        if (item.has("summary")) {
            item.putArray("summary");
        }
        if (item.has("content")) {
            item.putArray("content");
        }
        if (item.has("arguments")) {
            item.put("arguments", "");
        }
        item.put("status", "in_progress");
        return item;
    }

    private ObjectNode outputText(String text) {
        ObjectNode part = mapper.createObjectNode();
        part.put("type", "output_text");
        part.put("text", text);
        part.putArray("annotations");
        part.putArray("logprobs");
        return part;
    }

    private ObjectNode response(ChatReply reply, CapturedRequest req, String status, ArrayNode output) {
        ObjectNode root = mapper.createObjectNode();
        root.put("id", reply.id() != null ? reply.id() : "resp_mock_" + req.index());
        root.put("object", "response");
        root.put("created_at", Instant.now().getEpochSecond());
        root.put("status", status);
        root.putNull("access_programs");
        root.putNull("error");
        root.putNull("incomplete_details");
        root.putNull("instructions");
        root.put("model", Json.modelName(reply, req));
        root.putArray("tools");
        root.set("output", output);
        root.put("parallel_tool_calls", true);
        root.putObject("metadata");
        root.put("tool_choice", "auto");
        root.put("temperature", 1.0);
        root.put("top_p", 1.0);
        if ("completed".equals(status)) {
            OpenAiJson.responsesUsage(root, reply.usage());
        }
        return root;
    }
}
