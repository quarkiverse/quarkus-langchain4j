package io.quarkiverse.langchain4j.test.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 * An {@code Optional<T>} parameter of a {@code @Tool} method is supported by plain langchain4j:
 * {@code ToolSpecifications} unwraps it to a schema for {@code T} and leaves it out of
 * {@code required}, and {@code DefaultToolExecutor#prepareArguments} passes {@code Optional.empty()}
 * when the LLM omits the argument.
 * <p>
 * quarkus-langchain4j builds the tool schema and the argument array itself, so all of that has to be
 * mirrored here.
 */
public class ToolWithOptionalParameterTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(MyAiService.class, MyTools.class,
                            MyChatModelSupplier.class, MyChatModel.class, Lists.class));

    @Inject
    MyAiService aiService;

    /**
     * The LLM must be shown the schema of the <em>wrapped</em> type, and must not be asked for the
     * optional argument. Asserted on what the model actually receives in
     * {@link ChatRequest#toolSpecifications()}.
     */
    @Test
    @ActivateRequestContext
    void optionalParameterIsSchemaOfTheWrappedTypeAndNotRequired() {
        String schema = aiService.chat("schema");

        assertThat(schema).contains("country:JsonStringSchema");
        assertThat(schema).contains("city:JsonStringSchema");
        assertThat(schema).contains("|required=[country]");
    }

    @Test
    @ActivateRequestContext
    void suppliedOptionalArgumentIsPassedAsAPresentOptional() {
        assertThat(aiService.chat("with-city")).isEqualTo("response: country=France;city=present:Paris");
    }

    /**
     * The interesting one: with the argument omitted the tool must see {@code Optional.empty()}, not
     * {@code null}, otherwise every {@code Optional} tool parameter NPEs exactly when it is used as
     * intended.
     */
    @Test
    @ActivateRequestContext
    void omittedOptionalArgumentIsPassedAsAnEmptyOptional() {
        assertThat(aiService.chat("without-city")).isEqualTo("response: country=France;city=empty");
    }

    /**
     * Control: a non-optional parameter keeps its schema and its {@code required} entry.
     */
    @Test
    @ActivateRequestContext
    void explicitlyOptionalParameterStillBehavesAsBefore() {
        assertThat(aiService.chat("explicit-not-required"))
                .isEqualTo("response: zone=null");
    }

    @RegisterAiService(chatLanguageModelSupplier = MyChatModelSupplier.class, tools = MyTools.class)
    public interface MyAiService {
        String chat(@UserMessage String mode);
    }

    @ApplicationScoped
    public static class MyTools {

        @Tool("Get the weather of a city")
        public String weather(String country, Optional<String> city) {
            String renderedCity;
            if (city == null) {
                renderedCity = "NULL";
            } else if (city.isPresent()) {
                renderedCity = "present:" + city.get();
            } else {
                renderedCity = "empty";
            }
            return "country=" + country + ";city=" + renderedCity;
        }

        @Tool("Get the time")
        public String time(@P(value = "the time zone", required = false) String zone) {
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
                case "with-city":
                    return request("weather", "{\"country\":\"France\",\"city\":\"Paris\"}");
                case "without-city":
                    return request("weather", "{\"country\":\"France\"}");
                case "explicit-not-required":
                    return request("time", "{}");
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
                    .map(ToolWithOptionalParameterTest.MyChatModel::render)
                    .collect(Collectors.joining(","));
            return properties + "|required=" + parameters.required();
        }

        private static String render(Map.Entry<String, JsonSchemaElement> property) {
            return property.getKey() + ":" + property.getValue().getClass().getSimpleName();
        }
    }
}
