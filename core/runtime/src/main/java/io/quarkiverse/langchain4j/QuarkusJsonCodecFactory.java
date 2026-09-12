package io.quarkiverse.langchain4j;

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

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ContentType;
import dev.langchain4j.data.message.CustomMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.internal.Json;
import dev.langchain4j.spi.json.JsonCodecFactory;
import io.quarkiverse.langchain4j.runtime.jackson.CustomLocalDateDeserializer;
import io.quarkiverse.langchain4j.runtime.jackson.CustomLocalDateTimeDeserializer;
import io.quarkiverse.langchain4j.runtime.jackson.CustomLocalTimeDeserializer;
import io.quarkus.arc.Arc;
import tools.jackson.core.JacksonException;
import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleDeserializers;
import tools.jackson.databind.module.SimpleModule;

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
            } catch (JacksonException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public <T> T fromJson(String json, Class<T> type) {
            try {
                String sanitizedJson = sanitize(json, type);
                return ObjectMapperHolder.MAPPER.readValue(sanitizedJson, type);
            } catch (JacksonException e) {
                // Check if this is a parse error for an enum - LangChain4j sometimes passes raw enum string values
                if (type.isEnum()) {
                    try {
                        Class<? extends Enum> enumClass = type.asSubclass(Enum.class);
                        return (T) Enum.valueOf(enumClass, json);
                    } catch (IllegalArgumentException | NullPointerException enumEx) {
                        // Not a valid enum value, rethrow original exception
                    }
                }
                throw new RuntimeException(e);
            }
        }

        @Override
        public <T> T fromJson(String json, Type type) {
            JavaType javaType = ObjectMapperHolder.MAPPER.getTypeFactory().constructType(type);
            try {
                String sanitizedJson = sanitize(json, javaType.getRawClass());
                return ObjectMapperHolder.MAPPER.readValue(sanitizedJson, javaType);
            } catch (JacksonException e) {
                // Check if this is a parse error for an enum - LangChain4j sometimes passes raw enum string values
                if (javaType.isEnumType()) {
                    try {
                        Class<? extends Enum> enumClass = javaType.getRawClass().asSubclass(Enum.class);
                        return (T) Enum.valueOf(enumClass, json);
                    } catch (IllegalArgumentException | NullPointerException enumEx) {
                        // Not a valid enum value, rethrow original exception
                    }
                }
                throw new RuntimeException(e);
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
            MAPPER = SnakeCaseObjectMapperHolder.baseBuilder()
                    .disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                    .changeDefaultVisibility(vc -> vc
                            .withVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE)
                            .withFieldVisibility(JsonAutoDetect.Visibility.ANY))
                    .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
                    // Add chat message mixins to preserve thinking field deserialization
                    .addMixIn(ChatMessage.class, ChatMessageMixin.class)
                    .addMixIn(AiMessage.class, AiMessageMixin.class)
                    .addMixIn(UserMessage.class, UserMessageMixin.class)
                    .addMixIn(SystemMessage.class, SystemMessageMixin.class)
                    .addMixIn(ToolExecutionResultMessage.class, ToolExecutionResultMessageMixin.class)
                    .addMixIn(CustomMessage.class, CustomMessageMixin.class)
                    .addMixIn(ToolExecutionRequest.class, ToolExecutionRequestMixin.class)
                    .addMixIn(Content.class, ContentMixin.class)
                    .addMixIn(TextContent.class, TextContentMixin.class)
                    .addMixIn(ImageContent.class, ImageContentMixin.class)
                    .addMixIn(Image.class, ImageMixin.class)
                    // Register Quarkus-specific module
                    .addModule(SnakeCaseObjectMapperHolder.QuarkusLangChain4jModule.INSTANCE)
                    .build();

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
    @JsonDeserialize(builder = ToolExecutionResultMessage.Builder.class)
    @JsonPropertyOrder({ "id", "toolName", "type", "contents", "attributes" })
    private abstract static class ToolExecutionResultMessageMixin {
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

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = TextContent.class, name = "TEXT"),
            @JsonSubTypes.Type(value = ImageContent.class, name = "IMAGE"),
    })
    private abstract static class ContentMixin {
        @JsonProperty
        public abstract ContentType type();
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private abstract static class TextContentMixin {
        @JsonCreator
        public TextContentMixin(@JsonProperty("text") String text) {
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private abstract static class ImageContentMixin {
        @JsonCreator
        public ImageContentMixin(
                @JsonProperty("image") Image image,
                @JsonProperty("detailLevel") ImageContent.DetailLevel detailLevel) {
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonDeserialize(builder = Image.Builder.class)
    private abstract static class ImageMixin {
    }

    public static class SnakeCaseObjectMapperHolder {
        public static final ObjectMapper MAPPER = baseBuilder()
                .disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .changeDefaultPropertyInclusion(incl -> incl
                        .withValueInclusion(JsonInclude.Include.NON_NULL))
                .enable(SerializationFeature.INDENT_OUTPUT)
                .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
                .addModule(QuarkusLangChain4jModule.INSTANCE)
                .build();

        /** Quarkus 4 exposes a Jackson 3 mapper bean; fall back to Jackson 2 defaults for container-less tests. */
        static JsonMapper.Builder baseBuilder() {
            var handle = Arc.container().instance(ObjectMapper.class);
            JsonMapper.Builder builder;
            if (handle.isAvailable()) {
                ObjectMapper mapper = handle.get();
                // The mapper should be a JsonMapper in Quarkus 4
                if (mapper instanceof JsonMapper) {
                    builder = ((JsonMapper) mapper).rebuild();
                } else {
                    builder = JsonMapper.builderWithJackson2Defaults();
                }
            } else {
                builder = JsonMapper.builderWithJackson2Defaults();
            }
            // Ensure Jackson 2 compatibility - Quarkus bean may already have these, but explicit is safer
            return builder
                    .disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                    .enable(MapperFeature.ALLOW_FINAL_FIELDS_AS_MUTATORS)
                    .enable(MapperFeature.USE_GETTERS_AS_SETTERS);
        }

        static class QuarkusLangChain4jModule extends SimpleModule {

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
