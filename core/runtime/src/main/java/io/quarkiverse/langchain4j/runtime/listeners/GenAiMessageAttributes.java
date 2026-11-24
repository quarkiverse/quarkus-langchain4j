package io.quarkiverse.langchain4j.runtime.listeners;

import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.audio.Audio;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.AudioContent;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.VideoContent;
import dev.langchain4j.data.video.Video;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.KeyValue;
import io.opentelemetry.api.common.Value;
import io.quarkiverse.langchain4j.QuarkusJsonCodecFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;

/**
 * Builds the message content attributes of the GenAI semantic conventions
 * (<a href="https://opentelemetry.io/docs/specs/semconv/gen-ai/gen-ai-spans/">GenAI spans</a>).
 * <p>
 * The attribute keys are declared here because {@code opentelemetry-semconv-incubating} does not generate constants for
 * {@code any}-typed attributes. The values are recorded as structured {@code AnyValue} arrays that follow the semconv
 * message schemas, so that collectors and backends can read the {@code role} and {@code parts} of each message directly.
 */
final class GenAiMessageAttributes {

    static final AttributeKey<Value<?>> GEN_AI_SYSTEM_INSTRUCTIONS = AttributeKey.valueKey("gen_ai.system_instructions");
    static final AttributeKey<Value<?>> GEN_AI_INPUT_MESSAGES = AttributeKey.valueKey("gen_ai.input.messages");
    static final AttributeKey<Value<?>> GEN_AI_OUTPUT_MESSAGES = AttributeKey.valueKey("gen_ai.output.messages");

    /**
     * Deprecated in the GenAI semantic conventions in favor of {@link #GEN_AI_INPUT_MESSAGES} and
     * {@link #GEN_AI_SYSTEM_INSTRUCTIONS}. Still emitted so that existing consumers keep working.
     */
    static final AttributeKey<String> GEN_AI_PROMPT = AttributeKey.stringKey("gen_ai.prompt");

    /**
     * Deprecated in the GenAI semantic conventions in favor of {@link #GEN_AI_OUTPUT_MESSAGES}. Still emitted so that
     * existing consumers keep working.
     */
    static final AttributeKey<String> GEN_AI_COMPLETION = AttributeKey.stringKey("gen_ai.completion");

    /**
     * Providers whose APIs take the system instructions separately from the chat history. Every other provider sends
     * the system messages as part of the chat history, so they keep their position in {@link #GEN_AI_INPUT_MESSAGES}.
     */
    private static final Set<ModelProvider> SEPARATE_SYSTEM_INSTRUCTIONS_PROVIDERS = EnumSet.of(
            ModelProvider.ANTHROPIC,
            ModelProvider.AMAZON_BEDROCK,
            ModelProvider.GOOGLE_AI_GEMINI,
            ModelProvider.GOOGLE_GENAI,
            ModelProvider.GOOGLE_VERTEX_AI_GEMINI,
            ModelProvider.GOOGLE_VERTEX_AI_ANTHROPIC);

    private GenAiMessageAttributes() {
    }

    static boolean sendsSystemInstructionsSeparately(ModelProvider modelProvider) {
        return (modelProvider != null) && SEPARATE_SYSTEM_INSTRUCTIONS_PROVIDERS.contains(modelProvider);
    }

    static Optional<Value<?>> systemInstructions(List<ChatMessage> messages) {
        List<Value<?>> parts = new ArrayList<>();
        for (ChatMessage message : messages) {
            if (message instanceof SystemMessage systemMessage) {
                parts.add(textPart(systemMessage.text()));
            }
        }
        return parts.isEmpty() ? Optional.empty() : Optional.of(Value.of(parts));
    }

    static Value<?> inputMessages(List<ChatMessage> messages, boolean includeSystemMessages) {
        List<Value<?>> result = new ArrayList<>();
        for (ChatMessage message : messages) {
            if (message instanceof SystemMessage systemMessage) {
                if (includeSystemMessages) {
                    result.add(message("system", List.of(textPart(systemMessage.text()))));
                }
            } else if (message instanceof UserMessage userMessage) {
                result.add(userMessage(userMessage));
            } else if (message instanceof AiMessage aiMessage) {
                result.add(message("assistant", aiMessageParts(aiMessage)));
            } else if (message instanceof ToolExecutionResultMessage toolResult) {
                result.add(message("tool", List.of(toolResultPart(toolResult))));
            }
        }
        return Value.of(result);
    }

    static Value<?> outputMessages(ChatResponse response) {
        return Value.of(message("assistant", aiMessageParts(response.aiMessage())));
    }

    private static Value<?> userMessage(UserMessage userMessage) {
        List<KeyValue> fields = messageFields("user", contentParts(userMessage.contents()));
        putIfNotNull(fields, "name", userMessage.name());
        return object(fields);
    }

    private static List<Value<?>> contentParts(List<Content> contents) {
        List<Value<?>> parts = new ArrayList<>();
        for (Content content : contents) {
            parts.add(contentPart(content));
        }
        return parts;
    }

    private static Value<?> contentPart(Content content) {
        if (content instanceof TextContent textContent) {
            return textPart(textContent.text());
        }
        if (content instanceof ImageContent imageContent) {
            Image image = imageContent.image();
            return mediaPart("image", image.url(), image.base64Data(), image.mimeType());
        }
        if (content instanceof AudioContent audioContent) {
            Audio audio = audioContent.audio();
            return mediaPart("audio", audio.url(), audio.base64Data(), audio.mimeType());
        }
        if (content instanceof VideoContent videoContent) {
            Video video = videoContent.video();
            return mediaPart("video", video.url(), video.base64Data(), video.mimeType());
        }
        return object(List.of(KeyValue.of("type", Value.of(content.type().name().toLowerCase()))));
    }

    private static Value<?> mediaPart(String modality, URI url, String base64Data, String mimeType) {
        List<KeyValue> fields = new ArrayList<>();
        if (url != null) {
            fields.add(KeyValue.of("type", Value.of("uri")));
            fields.add(KeyValue.of("modality", Value.of(modality)));
            putIfNotNull(fields, "mime_type", mimeType);
            fields.add(KeyValue.of("uri", Value.of(url.toString())));
        } else {
            fields.add(KeyValue.of("type", Value.of("blob")));
            fields.add(KeyValue.of("modality", Value.of(modality)));
            putIfNotNull(fields, "mime_type", mimeType);
            Optional<byte[]> content = decodeBase64(base64Data);
            if (content.isPresent()) {
                fields.add(KeyValue.of("content", Value.of(content.get())));
            }
        }
        return object(fields);
    }

    /**
     * The semconv recommends recording binary content as bytes. A payload that is not valid base64 is omitted rather
     * than failing the whole attribute: the part still carries its modality and MIME type.
     */
    private static Optional<byte[]> decodeBase64(String base64Data) {
        Optional<byte[]> content = Optional.empty();
        if (base64Data != null) {
            try {
                content = Optional.of(Base64.getDecoder().decode(base64Data));
            } catch (IllegalArgumentException e) {
                // not base64, so the optional payload is left out
            }
        }
        return content;
    }

    private static List<Value<?>> aiMessageParts(AiMessage aiMessage) {
        List<Value<?>> parts = new ArrayList<>();
        if (aiMessage.thinking() != null) {
            parts.add(object(List.of(
                    KeyValue.of("type", Value.of("reasoning")),
                    KeyValue.of("content", Value.of(aiMessage.thinking())))));
        }
        if (aiMessage.text() != null) {
            parts.add(textPart(aiMessage.text()));
        }
        if (aiMessage.hasToolExecutionRequests()) {
            for (ToolExecutionRequest request : aiMessage.toolExecutionRequests()) {
                List<KeyValue> fields = new ArrayList<>();
                fields.add(KeyValue.of("type", Value.of("tool_call")));
                putIfNotNull(fields, "id", request.id());
                fields.add(KeyValue.of("name", Value.of(request.name())));
                fields.add(KeyValue.of("arguments", arguments(request.arguments())));
                parts.add(object(fields));
            }
        }
        return parts;
    }

    private static Value<?> toolResultPart(ToolExecutionResultMessage toolResult) {
        List<KeyValue> fields = new ArrayList<>();
        fields.add(KeyValue.of("type", Value.of("tool_call_response")));
        putIfNotNull(fields, "id", toolResult.id());
        fields.add(KeyValue.of("response", toolResponse(toolResult)));
        return object(fields);
    }

    private static Value<?> toolResponse(ToolExecutionResultMessage toolResult) {
        return toolResult.hasSingleText()
                ? Value.of(toolResult.text())
                : Value.of(contentParts(toolResult.contents()));
    }

    private static Value<?> arguments(String arguments) {
        if ((arguments == null) || arguments.isBlank()) {
            return object(List.of());
        }
        try {
            return toValue(QuarkusJsonCodecFactory.ObjectMapperHolder.MAPPER.readTree(arguments));
        } catch (JacksonException e) {
            return Value.of(arguments);
        }
    }

    private static Value<?> toValue(JsonNode node) {
        if (node.isObject()) {
            List<KeyValue> fields = new ArrayList<>();
            for (Map.Entry<String, JsonNode> property : node.properties()) {
                fields.add(KeyValue.of(property.getKey(), toValue(property.getValue())));
            }
            return object(fields);
        }
        if (node.isArray()) {
            List<Value<?>> elements = new ArrayList<>();
            for (JsonNode element : node) {
                elements.add(toValue(element));
            }
            return Value.of(elements);
        }
        if (node.isBoolean()) {
            return Value.of(node.booleanValue());
        }
        if (node.isIntegralNumber() && node.canConvertToLong()) {
            return Value.of(node.longValue());
        }
        if (node.isNumber()) {
            return Value.of(node.doubleValue());
        }
        if (node.isNull() || node.isMissingNode()) {
            return Value.empty();
        }
        return Value.of(node.asString());
    }

    private static Value<?> message(String role, List<Value<?>> parts) {
        return object(messageFields(role, parts));
    }

    private static List<KeyValue> messageFields(String role, List<Value<?>> parts) {
        List<KeyValue> fields = new ArrayList<>();
        fields.add(KeyValue.of("role", Value.of(role)));
        fields.add(KeyValue.of("parts", Value.of(parts)));
        return fields;
    }

    private static Value<?> textPart(String text) {
        return object(List.of(
                KeyValue.of("type", Value.of("text")),
                KeyValue.of("content", Value.of(text))));
    }

    private static Value<?> object(List<KeyValue> fields) {
        return Value.of(fields.toArray(new KeyValue[0]));
    }

    private static void putIfNotNull(List<KeyValue> fields, String name, String value) {
        if (value != null) {
            fields.add(KeyValue.of(name, Value.of(value)));
        }
    }
}
