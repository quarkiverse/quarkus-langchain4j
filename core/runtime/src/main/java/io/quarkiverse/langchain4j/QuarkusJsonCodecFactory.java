package io.quarkiverse.langchain4j;

import java.io.UncheckedIOException;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.module.SimpleDeserializers;
import com.fasterxml.jackson.databind.module.SimpleModule;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.CustomMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.internal.Json;
import dev.langchain4j.spi.json.JsonCodecFactory;
import io.quarkiverse.langchain4j.runtime.jackson.CustomLocalDateDeserializer;
import io.quarkiverse.langchain4j.runtime.jackson.CustomLocalDateTimeDeserializer;
import io.quarkiverse.langchain4j.runtime.jackson.CustomLocalTimeDeserializer;
import io.quarkus.arc.Arc;

public class QuarkusJsonCodecFactory implements JsonCodecFactory {

    @Override
    public Json.JsonCodec create() {
        return new Codec();
    }

    private static class Codec implements Json.JsonCodec {

        private static final Pattern sanitizePattern = Pattern.compile("(?s)\\{.*\\}|\\[.*\\]");

        @Override
        public String toJson(Object o) {
            try {
                return ObjectMapperHolder.WRITER.writeValueAsString(o);
            } catch (JsonProcessingException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public <T> T fromJson(String json, Class<T> type) {
            try {
                String sanitizedJson = sanitize(json, type);
                return ObjectMapperHolder.MAPPER.readValue(sanitizedJson, type);
            } catch (JsonProcessingException e) {
                if ((e instanceof JsonParseException) && (type.isEnum())) {
                    // this is the case where LangChain4j simply passes the string value of the enum to Json.fromJson()
                    // and Jackson does not handle it
                    Class<? extends Enum> enumClass = type.asSubclass(Enum.class);
                    return (T) Enum.valueOf(enumClass, json);
                }
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public <T> T fromJson(String json, Type type) {
            JavaType javaType = ObjectMapperHolder.MAPPER.constructType(type);
            try {
                String sanitizedJson = sanitize(json, javaType.getRawClass());
                return ObjectMapperHolder.MAPPER.readValue(sanitizedJson, javaType);
            } catch (JsonProcessingException e) {
                if ((e instanceof JsonParseException) && (javaType.isEnumType())) {
                    // this is the case where LangChain4j simply passes the string value of the enum to Json.fromJson()
                    // and Jackson does not handle it
                    Class<? extends Enum> enumClass = javaType.getRawClass().asSubclass(Enum.class);
                    return (T) Enum.valueOf(enumClass, json);
                }
                throw new UncheckedIOException(e);
            }
        }

        private <T> String sanitize(String original, Class<T> type) {
            if (String.class.equals(type)) {
                return original;
            }

            Matcher matcher = sanitizePattern.matcher(original);
            if (matcher.find()) {
                return matcher.group();
            }
            return original;
        }
    }

    public static class ObjectMapperHolder {
        public static final ObjectMapper MAPPER;
        public static final TypeReference<Map<String, Object>> MAP_TYPE_REFERENCE = new TypeReference<>() {
        };
        public static final ObjectWriter WRITER;

        static {
            // Start with Arc container ObjectMapper to preserve Quarkus integration
            MAPPER = Arc.container().instance(ObjectMapper.class).get()
                    .copy()
                    .setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE)
                    .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
                    .configure(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature(), true);

            // Add chat message mixins to preserve thinking field deserialization
            MAPPER.addMixIn(ChatMessage.class, ChatMessageMixin.class);
            MAPPER.addMixIn(AiMessage.class, AiMessageMixin.class);
            MAPPER.addMixIn(UserMessage.class, UserMessageMixin.class);
            MAPPER.addMixIn(SystemMessage.class, SystemMessageMixin.class);
            MAPPER.addMixIn(ToolExecutionResultMessage.class, ToolExecutionResultMessageMixin.class);
            MAPPER.addMixIn(CustomMessage.class, CustomMessageMixin.class);
            MAPPER.addMixIn(ToolExecutionRequest.class, ToolExecutionRequestMixin.class);

            // Register Quarkus-specific module
            MAPPER.registerModule(SnakeCaseObjectMapperHolder.QuarkusLangChain4jModule.INSTANCE);

            WRITER = MAPPER.writerWithDefaultPrettyPrinter();
        }
    }

    /**
     * Jackson mixins for chat message deserialization.
     * These enable proper deserialization of chat messages including the thinking field in AiMessage.
     * Based on mixins from dev.langchain4j.data.message.JacksonChatMessageJsonCodec.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = SystemMessage.class, name = "SYSTEM"),
            @JsonSubTypes.Type(value = UserMessage.class, name = "USER"),
            @JsonSubTypes.Type(value = AiMessage.class, name = "AI"),
            @JsonSubTypes.Type(value = ToolExecutionResultMessage.class, name = "TOOL_EXECUTION_RESULT"),
            @JsonSubTypes.Type(value = CustomMessage.class, name = "CUSTOM"),
    })
    private abstract static class ChatMessageMixin {
        @JsonProperty
        public abstract ChatMessageType type();
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private abstract static class SystemMessageMixin {
        @JsonCreator
        public SystemMessageMixin(@JsonProperty("text") String text) {
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonDeserialize(builder = UserMessage.Builder.class)
    private abstract static class UserMessageMixin {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonDeserialize(builder = AiMessage.Builder.class)
    @JsonPropertyOrder({ "toolExecutionRequests", "text", "attributes", "type" })
    private abstract static class AiMessageMixin {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({ "text", "id", "toolName", "type" })
    private static class ToolExecutionResultMessageMixin {
        @JsonCreator
        public ToolExecutionResultMessageMixin(
                @JsonProperty("id") String id,
                @JsonProperty("toolName") String toolName,
                @JsonProperty("text") String text) {
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private static class CustomMessageMixin {
        @JsonCreator
        public CustomMessageMixin(@JsonProperty("attributes") Map<String, Object> attributes) {
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonDeserialize(builder = ToolExecutionRequest.Builder.class)
    private abstract static class ToolExecutionRequestMixin {
    }

    public static class SnakeCaseObjectMapperHolder {
        public static final ObjectMapper MAPPER = Arc.container().instance(ObjectMapper.class).get()
                .copy()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .enable(SerializationFeature.INDENT_OUTPUT)
                .configure(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature(), true)
                .registerModule(QuarkusLangChain4jModule.INSTANCE);

        private static class QuarkusLangChain4jModule extends SimpleModule {

            private static final QuarkusLangChain4jModule INSTANCE = new QuarkusLangChain4jModule();

            @Override
            public String getModuleName() {
                return "QuarkusLangChain4jModule";
            }

            @Override
            public void setupModule(SetupContext context) {
                SimpleDeserializers desers = new SimpleDeserializers();
                desers.addDeserializer(LocalDate.class, new CustomLocalDateDeserializer());
                desers.addDeserializer(LocalDateTime.class, new CustomLocalDateTimeDeserializer());
                desers.addDeserializer(LocalTime.class, new CustomLocalTimeDeserializer());
                context.addDeserializers(desers);
            }
        }
    }

}
