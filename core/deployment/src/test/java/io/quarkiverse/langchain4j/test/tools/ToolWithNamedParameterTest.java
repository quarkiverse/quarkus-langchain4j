package io.quarkiverse.langchain4j.test.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.test.Lists;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * {@code @P(name = "...")} overrides the parameter name the LLM sees. Plain langchain4j honours it in both
 * directions: {@code ToolSpecifications} uses it as the property name (and in {@code required}), and
 * {@code DefaultToolExecutor#getName} maps the argument the LLM sends under that name back to the parameter.
 * <p>
 * quarkus-langchain4j builds the schema, the argument mapper and the argument array itself, so the same has to
 * hold here: the schema must carry the LLM-facing name, and an argument sent under it must reach the tool.
 */
public class ToolWithNamedParameterTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(MyAiService.class, MyTools.class,
                            MyChatModelSupplier.class, MyChatModel.class, Lists.class));

    @Inject
    MyAiService aiService;

    /**
     * Asserted on what the model actually receives in {@link ChatRequest#toolSpecifications()}: the property and
     * its {@code required} entry use the {@code @P} name, and the Java parameter name does not leak out.
     */
    @Test
    @ActivateRequestContext
    void schemaUsesTheNameFromP() {
        String schema = aiService.chat("schema");

        assertThat(schema).contains("country:JsonStringSchema");
        assertThat(schema).contains("city_name:JsonStringSchema");
        assertThat(schema).doesNotContain("city:");
        assertThat(schema).contains("|required=[country, city_name]");
    }

    /**
     * The LLM answers with the name it was shown, so that is the key the argument arrives under.
     */
    @Test
    @ActivateRequestContext
    void argumentSentUnderTheNameFromPReachesTheParameter() {
        assertThat(aiService.chat("weather")).isEqualTo("response: country=France;city=Paris");
    }

    /**
     * A default value is keyed by the same LLM-facing name, so it still applies when the argument is omitted.
     */
    @Test
    @ActivateRequestContext
    void defaultValueStillAppliesToARenamedParameter() {
        assertThat(aiService.chat("time-default")).isEqualTo("response: zone=UTC");
    }

    @Test
    @ActivateRequestContext
    void explicitArgumentOverridesTheDefaultOfARenamedParameter() {
        assertThat(aiService.chat("time-explicit")).isEqualTo("response: zone=Europe/Prague");
    }

    @RegisterAiService(chatLanguageModelSupplier = MyChatModelSupplier.class, tools = MyTools.class)
    public interface MyAiService {
        String chat(@UserMessage String mode);
    }

    @ApplicationScoped
    public static class MyTools {

        @Tool("Get the weather of a city")
        public String weather(String country, @P(name = "city_name", value = "the city") String city) {
            return "country=" + country + ";city=" + city;
        }

        @Tool("Get the time")
        public String time(@P(name = "tz", value = "the time zone", defaultValue = "UTC") String zone) {
            return "zone=" + zone;
        }
    }

    public static class MyChatModelSupplier implements Supplier<ChatModel> {
        @Override
        public ChatModel get() {
            return new MyChatModel();
        }
    }

    public static class MyChatModel implements ChatModel {

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            List<ChatMessage> messages = chatRequest.messages();
            if (messages.size() == 1) {
                String mode = ((dev.langchain4j.data.message.UserMessage) messages.get(0)).singleText();
                if ("schema".equals(mode)) {
                    return ChatResponse.builder()
                            .aiMessage(AiMessage.from(render(chatRequest.toolSpecifications(), "weather")))
                            .finishReason(FinishReason.STOP)
                            .build();
                }
                return ChatResponse.builder()
                        .aiMessage(new AiMessage("", List.of(toolCall(mode))))
                        .tokenUsage(new TokenUsage(0, 0))
                        .finishReason(FinishReason.TOOL_EXECUTION)
                        .build();
            } else if (messages.size() == 3) {
                ToolExecutionResultMessage toolResult = (ToolExecutionResultMessage) Lists.last(messages);
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from("response: " + toolResult.text()))
                        .finishReason(FinishReason.STOP)
                        .build();
            }
            throw new RuntimeException("Unexpected number of messages: " + messages.size());
        }

        private static ToolExecutionRequest toolCall(String mode) {
            switch (mode) {
                case "weather":
                    return request("weather", "{\"country\":\"France\",\"city_name\":\"Paris\"}");
                case "time-default":
                    return request("time", "{}");
                case "time-explicit":
                    return request("time", "{\"tz\":\"Europe/Prague\"}");
                default:
                    throw new RuntimeException("Unexpected mode: " + mode);
            }
        }

        private static ToolExecutionRequest request(String name, String arguments) {
            return ToolExecutionRequest.builder()
                    .id("tool-" + name)
                    .name(name)
                    .arguments(arguments)
                    .build();
        }

        /**
         * Renders the parameter schema of one tool as {@code name:SchemaType,...|required=[...]} so the
         * assertions can run outside the QuarkusExtensionTest classloader.
         */
        private static String render(List<ToolSpecification> toolSpecifications, String toolName) {
            ToolSpecification spec = toolSpecifications.stream()
                    .filter(s -> toolName.equals(s.name()))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("No tool named " + toolName));
            JsonObjectSchema parameters = spec.parameters();
            String properties = parameters.properties().entrySet().stream()
                    .map(ToolWithNamedParameterTest.MyChatModel::render)
                    .collect(Collectors.joining(","));
            return properties + "|required=" + parameters.required();
        }

        private static String render(Map.Entry<String, JsonSchemaElement> property) {
            return property.getKey() + ":" + property.getValue().getClass().getSimpleName();
        }
    }
}
