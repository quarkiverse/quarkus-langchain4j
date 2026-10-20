package org.acme.examples.aiservices;

import static dev.langchain4j.data.message.ChatMessageDeserializer.messagesFromJson;
import static dev.langchain4j.data.message.ChatMessageSerializer.messagesToJson;
import static dev.langchain4j.data.message.ChatMessageType.AI;
import static dev.langchain4j.data.message.ChatMessageType.SYSTEM;
import static dev.langchain4j.data.message.ChatMessageType.USER;
import static java.time.Month.JULY;
import static org.acme.examples.aiservices.OpenAiRequestAssertions.assertMultipleRequestMessage;
import static org.acme.examples.aiservices.OpenAiRequestAssertions.assertSingleRequestMessage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;

import org.acme.examples.aiservices.OpenAiRequestAssertions.MessageContent;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.annotation.JsonCreator;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.input.structured.StructuredPrompt;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiModerationModel;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.model.output.structured.Description;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.Moderate;
import dev.langchain4j.service.ModerationException;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkiverse.langchain4j.mockapi.ModerationReply;
import io.quarkiverse.langchain4j.mockapi.RawResponse;
import io.quarkiverse.langchain4j.runtime.LangChain4jUtil;
import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusExtensionTest;

public class AiServicesTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class)
                            .addClass(OpenAiRequestAssertions.class)
                            .addAsResource("messages/recipe-user.txt")
                            .addAsResource("messages/translate-user.txt")
                            .addAsResource("messages/translate-system"))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            // about the Chat Completions API: without a base URL of its own the model would target the Responses API
            .overrideConfigKey("quarkus.langchain4j.openai.chat-model.mode", "chat-completion")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever");

    @Inject
    MockModelServer mock;

    @ConfigProperty(name = "quarkus.langchain4j.openai.base-url")
    String baseUrl;

    private OpenAiChatModel createChatModel() {
        return OpenAiChatModel.builder().baseUrl(baseUrl)
                .logRequests(true)
                .logResponses(true)
                .maxRetries(1)
                .apiKey("whatever").build();
    }

    private OpenAiModerationModel createModerationModel() {
        return OpenAiModerationModel.builder().baseUrl(baseUrl)
                .logRequests(true)
                .logResponses(true)
                .apiKey("whatever").build();
    }

    private static MessageWindowChatMemory createChatMemory() {
        return MessageWindowChatMemory.withMaxMessages(10);
    }

    @BeforeEach
    void setup() {
        mock.reset();
        clearDefaultMemory();
    }

    private void clearDefaultMemory() {
        Arc.container().instance(ChatMemoryStore.class).get().deleteMessages("default");
    }

    interface Assistant {

        String chat(String message);
    }

    @Test
    public void test_simple_instruction_with_single_argument_and_no_annotations() {
        String result = AiServices.create(Assistant.class, createChatModel()).chat("Tell me a joke about developers");
        assertThat(result).isNotBlank();

        assertSingleRequestMessage(mock.chat().singleRequest().bodyAsJson(), "Tell me a joke about developers");
    }

    interface Humorist {

        @UserMessage("Tell me a joke about {{wrapper.topic}}")
        String joke(@SpanAttribute @NotNull Wrapper wrapper);
    }

    public record Wrapper(String topic) {
    }

    @Test
    public void test_simple_instruction_with_single_argument() {
        String result = AiServices.create(Humorist.class, createChatModel()).joke(new Wrapper("programmers"));
        assertThat(result).isNotBlank();

        assertSingleRequestMessage(mock.chat().singleRequest().bodyAsJson(), "Tell me a joke about programmers");
    }

    interface DateTimeExtractor {

        @UserMessage("Extract date from {{it}}")
        LocalDate extractDateFrom(String text);

        @UserMessage("Extract time from {{it}}")
        LocalTime extractTimeFrom(String text);

        @UserMessage("Extract date and time from {{it}}")
        LocalDateTime extractDateTimeFrom(String text);
    }

    @Test
    void test_extract_date() {
        mock.chat().reply("1968-07-04");
        DateTimeExtractor dateTimeExtractor = AiServices.create(DateTimeExtractor.class, createChatModel());

        LocalDate result = dateTimeExtractor.extractDateFrom(
                "The tranquility pervaded the evening of 1968, just fifteen minutes shy of midnight, following the celebrations of Independence Day.");
        assertThat(result).isEqualTo(LocalDate.of(1968, JULY, 4));

        assertSingleRequestMessage(mock.chat().singleRequest().bodyAsJson(),
                "Extract date from The tranquility pervaded the evening of 1968, just fifteen minutes shy of midnight, following the celebrations of Independence Day.\nYou must answer strictly in the following format: yyyy-MM-dd");
    }

    @Test
    void test_extract_time() {
        mock.chat().reply("23:45:00");
        DateTimeExtractor dateTimeExtractor = AiServices.create(DateTimeExtractor.class, createChatModel());

        LocalTime result = dateTimeExtractor.extractTimeFrom(
                "The tranquility pervaded the evening of 1968, just fifteen minutes shy of midnight, following the celebrations of Independence Day.");
        assertThat(result).isEqualTo(LocalTime.of(23, 45, 0));

        assertSingleRequestMessage(mock.chat().singleRequest().bodyAsJson(),
                "Extract time from The tranquility pervaded the evening of 1968, just fifteen minutes shy of midnight, following the celebrations of Independence Day.\nYou must answer strictly in the following format: HH:mm:ss");
    }

    @Test
    void test_extract_date_time() {
        mock.chat().reply("1968-07-04T23:45:00");
        DateTimeExtractor dateTimeExtractor = AiServices.create(DateTimeExtractor.class, createChatModel());

        LocalDateTime result = dateTimeExtractor.extractDateTimeFrom(
                "The tranquility pervaded the evening of 1968, just fifteen minutes shy of midnight, following the celebrations of Independence Day.");
        assertThat(result).isEqualTo(LocalDateTime.of(1968, JULY, 4, 23, 45, 0));

        assertSingleRequestMessage(mock.chat().singleRequest().bodyAsJson(),
                "Extract date and time from The tranquility pervaded the evening of 1968, just fifteen minutes shy of midnight, following the celebrations of Independence Day.\nYou must answer strictly in the following format: yyyy-MM-ddTHH:mm:ss");
    }

    interface TextOperator {
        LocalDateTime doAnythingWithText(@UserMessage String userMessage, @V("text") String text);
    }

    @Test
    void test_extract_date_from_text() {
        mock.chat().reply("1968-07-04T23:45:00");
        TextOperator textOperator = AiServices.create(TextOperator.class, createChatModel());

        String text = "The tranquility pervaded the evening of 1968, just fifteen minutes shy of midnight, following the celebrations of Independence Day.";

        LocalDateTime result = textOperator.doAnythingWithText("Extract date and time from {{text}}", text);
        assertThat(result).isEqualTo(LocalDateTime.of(1968, JULY, 4, 23, 45, 0));

        assertSingleRequestMessage(mock.chat().singleRequest().bodyAsJson(),
                "Extract date and time from The tranquility pervaded the evening of 1968, just fifteen minutes shy of midnight, following the celebrations of Independence Day.\nYou must answer strictly in the following format: yyyy-MM-ddTHH:mm:ss");
    }

    enum Sentiment {
        POSITIVE,
        NEUTRAL,
        NEGATIVE
    }

    interface SentimentAnalyzer {

        @UserMessage("Analyze sentiment of {{it}}")
        Sentiment analyzeSentimentOf(String text);
    }

    @Test
    void test_extract_enum() {
        mock.chat().reply("POSITIVE");
        SentimentAnalyzer sentimentAnalyzer = AiServices.create(SentimentAnalyzer.class, createChatModel());

        Sentiment sentiment = sentimentAnalyzer.analyzeSentimentOf(
                "This LaptopPro X15 is wicked fast and that 4K screen is a dream.");

        assertThat(sentiment).isEqualTo(Sentiment.POSITIVE);

        assertSingleRequestMessage(mock.chat().singleRequest().bodyAsJson(),
                "Analyze sentiment of This LaptopPro X15 is wicked fast and that 4K screen is a dream.\nYou must answer strictly with one of these enums:\nPOSITIVE\nNEUTRAL\nNEGATIVE");
    }

    record Person(String firstName, String lastName, LocalDate birthDate) {
        @JsonCreator
        public Person {
        }
    }

    interface PersonExtractor {

        @UserMessage("Extract information about a person from {{it}}")
        Person extractPersonFrom(String text);
    }

    @Test
    void test_extract_custom_POJO() {
        mock.chat().reply(
                "{\n\"firstName\": \"John\",\n\"lastName\": \"Doe\",\n\"birthDate\": \"1968-07-04\"\n}");
        PersonExtractor personExtractor = AiServices.create(PersonExtractor.class, createChatModel());

        String text = "In 1968, amidst the fading echoes of Independence Day, "
                + "a child named John arrived under the calm evening sky. "
                + "This newborn, bearing the surname Doe, marked the start of a new journey.";

        Person result = personExtractor.extractPersonFrom(text);

        assertThat(result.firstName).isEqualTo("John");
        assertThat(result.lastName).isEqualTo("Doe");
        assertThat(result.birthDate).isEqualTo(LocalDate.of(1968, JULY, 4));

        assertSingleRequestMessage(mock.chat().singleRequest().bodyAsJson(),
                "Extract information about a person from In 1968, amidst the fading echoes of Independence Day, " +
                        "a child named John arrived under the calm evening sky. This newborn, bearing the surname Doe, " +
                        "marked the start of a new journey.\nYou must answer strictly in the following JSON format: " +
                        "{\n\"firstName\": (type: string),\n\"lastName\": (type: string),\n\"birthDate\": (type: date string (2023-12-31))\n}");
    }

    static class Recipe {

        private String title;
        private String description;
        @Description("each step should be described in 4 words, steps should rhyme")
        private String[] steps;
        private Integer preparationTimeMinutes;
    }

    @StructuredPrompt("Create a recipe of a {{dish}} that can be prepared using only {{ingredients}}")
    static class CreateRecipePrompt {

        private final String dish;
        private final List<String> ingredients;

        public CreateRecipePrompt(String dish, List<String> ingredients) {
            this.dish = dish;
            this.ingredients = ingredients;
        }

        public String getDish() {
            return dish;
        }

        public List<String> getIngredients() {
            return ingredients;
        }
    }

    interface Chef {

        @UserMessage(fromResource = "messages/recipe-user.txt")
        Recipe createRecipeFrom(String... ingredients);

        Recipe createRecipeFrom(CreateRecipePrompt prompt);

        @SystemMessage("You are a very {{character}} chef")
        Recipe createRecipeFrom(@UserMessage CreateRecipePrompt prompt, String character);
    }

    @Test
    void test_create_recipe_from_list_of_ingredients() {
        Stream.of(
                "```json\n{\n\"title\": \"Greek Salad\",\n\"description\": \"A refreshing and tangy salad with Mediterranean flavors.\",\n\"steps\": [\n\"Chop, dice, and slice.\",\n\"Mix veggies with feta.\",\n\"Drizzle with olive oil.\",\n\"Toss gently, then serve.\"\n],\n\"preparationTimeMinutes\": 15\n}\n```",
                "```\n{\n\"title\": \"Greek Salad\",\n\"description\": \"A refreshing and tangy salad with Mediterranean flavors.\",\n\"steps\": [\n\"Chop, dice, and slice.\",\n\"Mix veggies with feta.\",\n\"Drizzle with olive oil.\",\n\"Toss gently, then serve.\"\n],\n\"preparationTimeMinutes\": 15\n}\n```",
                "```json\n{\n\"title\": \"Greek Salad\",\n\"description\": \"A refreshing and tangy salad with Mediterranean flavors.\",\n\"steps\": [\n\"Chop, dice, and slice.\",\n\"Mix veggies with feta.\",\n\"Drizzle with olive oil.\",\n\"Toss gently, then serve.\"\n],\n\"preparationTimeMinutes\": 15\n}\n",
                "```\n{\n\"title\": \"Greek Salad\",\n\"description\": \"A refreshing and tangy salad with Mediterranean flavors.\",\n\"steps\": [\n\"Chop, dice, and slice.\",\n\"Mix veggies with feta.\",\n\"Drizzle with olive oil.\",\n\"Toss gently, then serve.\"\n],\n\"preparationTimeMinutes\": 15\n}\n",
                "Woooow, do you want to know how to make a Greek salad? Here we go:```\n{\n\"title\": \"Greek Salad\",\n\"description\": \"A refreshing and tangy salad with Mediterranean flavors.\",\n\"steps\": [\n\"Chop, dice, and slice.\",\n\"Mix veggies with feta.\",\n\"Drizzle with olive oil.\",\n\"Toss gently, then serve.\"\n],\n\"preparationTimeMinutes\": 15\n}\n",
                "```\n{\n\"title\": \"Greek Salad\",\n\"description\": \"A refreshing and tangy salad with Mediterranean flavors.\",\n\"steps\": [\n\"Chop, dice, and slice.\",\n\"Mix veggies with feta.\",\n\"Drizzle with olive oil.\",\n\"Toss gently, then serve.\"\n],\n\"preparationTimeMinutes\": 15\n}\n\nYou can also add some tofu if you like!!!",
                "I'm here to help you {\n\"title\": \"Greek Salad\",\n\"description\": \"A refreshing and tangy salad with Mediterranean flavors.\",\n\"steps\": [\n\"Chop, dice, and slice.\",\n\"Mix veggies with feta.\",\n\"Drizzle with olive oil.\",\n\"Toss gently, then serve.\"\n],\n\"preparationTimeMinutes\": 15\n}")

                .forEach(value -> {

                    mock.reset();
                    clearDefaultMemory();

                    mock.chat().reply(value);
                    Chef chef = AiServices.create(Chef.class, createChatModel());

                    Recipe result = chef.createRecipeFrom("cucumber", "tomato", "feta", "onion", "olives");

                    assertThat(result.title).isNotBlank();
                    assertThat(result.description).isNotBlank();
                    assertThat(result.steps).isNotEmpty();
                    assertThat(result.preparationTimeMinutes).isPositive();

                    assertSingleRequestMessage(mock.chat().singleRequest().bodyAsJson(),
                            "Create recipe using only [cucumber, tomato, feta, onion, olives]\nYou must answer strictly in the following JSON format: "
                                    +
                                    "{\n\"title\": (type: string),\n\"description\": (type: string),\n\"steps\": (each step should be described in 4 words, steps should rhyme; type: array of string),\n\"preparationTimeMinutes\": (type: integer)\n}");
                });
    }

    @Test
    void test_create_recipe_using_structured_prompt() {
        mock.chat().reply(
                "{\n\"title\": \"Greek Salad\",\n\"description\": \"A refreshing and tangy salad with Mediterranean flavors.\",\n\"steps\": [\n\"Chop, dice, and slice.\",\n\"Mix veggies with feta.\",\n\"Drizzle with olive oil.\",\n\"Toss gently, then serve.\"\n],\n\"preparationTimeMinutes\": 15\n}");
        Chef chef = AiServices.create(Chef.class, createChatModel());

        Recipe result = chef
                .createRecipeFrom(new CreateRecipePrompt("salad", List.of("cucumber", "tomato", "feta", "onion", "olives")));

        assertThat(result.title).isNotBlank();
        assertThat(result.description).isNotBlank();
        assertThat(result.steps).isNotEmpty();
        assertThat(result.preparationTimeMinutes).isPositive();

        assertSingleRequestMessage(mock.chat().singleRequest().bodyAsJson(),
                "Create a recipe of a salad that can be prepared using only [cucumber, tomato, feta, onion, olives]\nYou must answer strictly in the following JSON format: "
                        +
                        "{\n\"title\": (type: string),\n\"description\": (type: string),\n\"steps\": (each step should be described in 4 words, steps should rhyme; type: array of string),\n\"preparationTimeMinutes\": (type: integer)\n}");
    }

    @Test
    void test_create_recipe_using_structured_prompt_and_system_message() {
        mock.chat().reply(
                "{\n\"title\": \"Greek Medley Salad\",\n\"description\": \"A refreshing and tangy salad with a Mediterranean twist.\",\n\"steps\": [\n\"Slice and dice, precise!\",\n\"Mix and toss, no loss!\",\n\"Sprinkle feta, get betta!\",\n\"Garnish with olives, no jives!\"\n],\n\"preparationTimeMinutes\": 15\n}");
        Chef chef = AiServices.create(Chef.class, createChatModel());

        Recipe result = chef
                .createRecipeFrom(new CreateRecipePrompt("salad", List.of("cucumber", "tomato", "feta", "onion", "olives")),
                        "funny");

        assertThat(result.title).isEqualTo("Greek Medley Salad");
        assertThat(result.description).isNotBlank();
        assertThat(result.steps).hasSize(4).satisfies(strings -> {
            assertThat(strings[0]).contains("Slice and dice");
            assertThat(strings[3]).contains("jives");
        });
        assertThat(result.preparationTimeMinutes).isEqualTo(15);

        assertMultipleRequestMessage(mock.chat().singleRequest().bodyAsJson(),
                List.of(
                        new MessageContent("system", "You are a very funny chef"),
                        new MessageContent("user",
                                "Create a recipe of a salad that can be prepared using only [cucumber, tomato, feta, onion, olives]\n"
                                        +
                                        "You must answer strictly in the following JSON format: " +
                                        "{\n\"title\": (type: string),\n\"description\": (type: string),\n\"steps\": (each step should be described in 4 words, steps should rhyme; type: array of string),\n\"preparationTimeMinutes\": (type: integer)\n}")));
    }

    @SystemMessage("You are a professional chef. You are friendly, polite and concise.")
    interface ProfessionalChef {

        String answer(String question);

        @SystemMessage("You are an amateur.")
        String answer2(String question);
    }

    @Test
    void test_with_system_message_of_first_method() {
        mock.chat().reply(
                "Grilling chicken typically takes around 10-15 minutes per side, depending on the thickness of the chicken. It's important to ensure the internal temperature reaches 165°F (74°C) for safe consumption.");
        ProfessionalChef chef = AiServices.create(ProfessionalChef.class, createChatModel());

        String result = chef.answer("How long should I grill chicken?");
        assertThat(result).contains("Grilling chicken typically");

        assertMultipleRequestMessage(mock.chat().singleRequest().bodyAsJson(),
                List.of(
                        new MessageContent("system", "You are a professional chef. You are friendly, polite and concise."),
                        new MessageContent("user",
                                "How long should I grill chicken?")));
    }

    @Test
    void test_with_system_message_of_second_method() {
        mock.chat().reply(
                "Grilling chicken typically takes around 10-15 minutes per side, depending on the thickness of the chicken. It's important to ensure the internal temperature reaches 165°F (74°C) for safe consumption.");
        ProfessionalChef chef = AiServices.create(ProfessionalChef.class, createChatModel());

        String result = chef.answer2("How long should I grill chicken?");
        assertThat(result).contains("Grilling chicken typically");

        assertMultipleRequestMessage(mock.chat().singleRequest().bodyAsJson(),
                List.of(
                        new MessageContent("system", "You are an amateur."),
                        new MessageContent("user",
                                "How long should I grill chicken?")));
    }

    interface Translator {

        @SystemMessage(fromResource = "messages/translate-system")
        @UserMessage(fromResource = "/messages/translate-user.txt")
        String translate(@V("text") String text, @V("lang") String language);
    }

    @Test
    void test_with_system_and_user_messages() {
        mock.chat().reply("Hallo, wie geht es dir?");
        Translator translator = AiServices.create(Translator.class, createChatModel());

        String translation = translator.translate("Hello, how are you?", "german");

        assertThat(translation).isEqualTo("Hallo, wie geht es dir?");

        assertMultipleRequestMessage(mock.chat().singleRequest().bodyAsJson(),
                List.of(
                        new MessageContent("system", "You are a professional translator into german"),
                        new MessageContent("user",
                                "Translate the following text: Hello, how are you?")));
    }

    interface Summarizer {

        @SystemMessage("Summarize every message from user in {{n}} bullet points. Provide only bullet points.")
        List<String> summarize(@UserMessage String text, @MemoryId int n);
    }

    @Test
    void test_with_system_message_and_user_message_as_argument() {
        mock.chat().reply(
                "- AI is a branch of computer science\n- AI aims to create machines that mimic human intelligence\n- AI can perform tasks like recognizing patterns, making decisions, and predictions");
        Summarizer summarizer = AiServices.create(Summarizer.class, createChatModel());

        String text = "AI, or artificial intelligence, is a branch of computer science that aims to create " +
                "machines that mimic human intelligence. This can range from simple tasks such as recognizing " +
                "patterns or speech to more complex tasks like making decisions or predictions.";

        List<String> bulletPoints = summarizer.summarize(text, 3);

        assertThat(bulletPoints).hasSize(3).satisfies(list -> {
            assertThat(list.get(0)).contains("branch");
            assertThat(list.get(2)).contains("predictions");
        });

        assertMultipleRequestMessage(mock.chat().singleRequest().bodyAsJson(),
                List.of(
                        new MessageContent("system",
                                "Summarize every message from user in 3 bullet points. Provide only bullet points."),
                        new MessageContent("user", text + "\nYou must put every item on a separate line.")));
    }

    interface SummarizerWithoutUserMessage {

        @SystemMessage("""
                Summarize every message from user in {{n}} bullet points. Provide only bullet points.

                The message is:

                {{message}}
                """)
        List<String> summarize(String message, int n);
    }

    @Test
    void test_with_system_message_and_no_user_message() {
        mock.chat().reply(
                "- AI is a branch of computer science\n- AI aims to create machines that mimic human intelligence\n- AI can perform tasks like recognizing patterns, making decisions, and predictions");
        SummarizerWithoutUserMessage summarizer = AiServices.create(SummarizerWithoutUserMessage.class, createChatModel());

        String text = "AI, or artificial intelligence, is a branch of computer science that aims to create " +
                "machines that mimic human intelligence. This can range from simple tasks such as recognizing " +
                "patterns or speech to more complex tasks like making decisions or predictions.";

        List<String> bulletPoints = summarizer.summarize(text, 3);

        assertThat(bulletPoints).hasSize(3).satisfies(list -> {
            assertThat(list.get(0)).contains("branch");
            assertThat(list.get(2)).contains("predictions");
        });

        assertMultipleRequestMessage(mock.chat().singleRequest().bodyAsJson(),
                List.of(
                        new MessageContent("system",
                                """
                                        Summarize every message from user in 3 bullet points. Provide only bullet points.

                                        The message is:

                                        AI, or artificial intelligence, is a branch of computer science that aims to create machines that mimic human intelligence. This can range from simple tasks such as recognizing patterns or speech to more complex tasks like making decisions or predictions.
                                        """),
                        new MessageContent("user",
                                "Continue output. DO NOT look at this line. ONLY look at the content before this line and system instruction.")));
    }

    interface ChatWithModeration {

        @Moderate
        String chat(String message);
    }

    @Test
    void should_throw_when_text_is_flagged() {
        mock.moderation().reply(ModerationReply.flagged("hate", "hate/threatening"));
        ChatWithModeration chatWithModeration = AiServices.builder(ChatWithModeration.class)
                .chatModel(createChatModel())
                .moderationModel(createModerationModel())
                .build();

        assertThatThrownBy(() -> chatWithModeration.chat("I WILL KILL YOU!!!"))
                .isExactlyInstanceOf(ModerationException.class)
                .hasMessage("Text \"" + "I WILL KILL YOU!!!" + "\" violates content policy");

        assertModerationRequest();
    }

    @Test
    void should_not_throw_when_text_is_not_flagged() {
        mock.moderation().reply(ModerationReply.notFlagged());
        ChatWithModeration chatWithModeration = AiServices.builder(ChatWithModeration.class)
                .chatModel(createChatModel())
                .moderationModel(createModerationModel())
                .build();

        String result = chatWithModeration.chat("I will hug you");
        assertThat(result).isNotBlank();

        assertModerationRequest();
    }

    private void assertModerationRequest() {
        CapturedRequest request = mock.moderation().singleRequest();
        assertThat(request.path()).isEqualTo("/v1/moderations");
        assertThat(request.query()).isNull();
        assertThat(request.header("Authorization")).isEqualTo("Bearer whatever");
    }

    interface ChatWithMemory {

        String chatWithoutSystemMessage(String userMessage);

        @SystemMessage("You are helpful assistant")
        String chatWithSystemMessage(String userMessage);

        @SystemMessage("You are funny assistant")
        String chatWithAnotherSystemMessage(String userMessage);
    }

    @Test
    void should_keep_chat_memory() {
        MessageWindowChatMemory chatMemory = createChatMemory();
        ChatWithMemory chatWithMemory = AiServices.builder(ChatWithMemory.class)
                .chatModel(createChatModel())
                .chatMemory(chatMemory)
                .build();

        /* **** First request **** */
        String firstUserMessage = "Hello, my name is Klaus";
        mock.chat().reply("Nice to meet you Klaus");
        String firstAiMessage = chatWithMemory.chatWithoutSystemMessage(firstUserMessage);

        // assert response
        assertThat(firstAiMessage).isEqualTo("Nice to meet you Klaus");

        // assert request
        assertSingleRequestMessage(mock.chat().singleRequest().bodyAsJson(), firstUserMessage);

        // assert chat memory
        assertThat(chatMemory.messages()).hasSize(2)
                .extracting(ChatMessage::type, LangChain4jUtil::chatMessageToText)
                .containsExactly(tuple(USER, firstUserMessage), tuple(AI, firstAiMessage));

        /* **** Second request **** */
        mock.reset();

        String secondUserMessage = "What is my name?";
        mock.chat().reply("Your name is Klaus");
        String secondAiMessage = chatWithMemory.chatWithoutSystemMessage(secondUserMessage);

        // assert response
        assertThat(secondAiMessage).contains("Klaus");

        // assert request
        assertMultipleRequestMessage(mock.chat().singleRequest().bodyAsJson(),
                List.of(
                        new MessageContent("user", firstUserMessage),
                        new MessageContent("assistant", firstAiMessage),
                        new MessageContent("user", secondUserMessage)));

        // assert chat memory
        assertThat(chatMemory.messages()).hasSize(4)
                .extracting(ChatMessage::type, LangChain4jUtil::chatMessageToText)
                .containsExactly(tuple(USER, firstUserMessage), tuple(AI, firstAiMessage), tuple(USER, secondUserMessage),
                        tuple(AI, secondAiMessage));
    }

    @Test
    void should_not_keep_memory_on_failed_call() {
        MessageWindowChatMemory chatMemory = createChatMemory();
        ChatWithMemory chatWithMemory = AiServices.builder(ChatWithMemory.class)
                .chatModel(createChatModel())
                .chatMemory(chatMemory)
                .build();

        String firstUserMessage = "Hello, my name is Klaus";
        // the unescaped message content makes the response body malformed JSON, which causes a JSON processing exception
        mock.raw().on("POST", "/v1/chat/completions").reply(200, """
                {
                  "id": "chatcmpl-123",
                  "object": "chat.completion",
                  "created": 1698140213,
                  "choices": [{
                    "index": 0,
                    "message": {
                      "role": "assistant",
                      "content": "{"test""
                    },
                    "finish_reason": "stop"
                  }],
                  "usage": {
                    "prompt_tokens": 9,
                    "completion_tokens": 12,
                    "total_tokens": 21
                  }
                }
                """);
        assertThatThrownBy(() -> chatWithMemory.chatWithoutSystemMessage(firstUserMessage));
        assertThat(mock.requests()).isNotEmpty();

        // assert chat memory
        assertThat(chatMemory.messages()).isEmpty();
    }

    @Test
    void should_keep_chat_memory_and_not_duplicate_system_message() {
        MessageWindowChatMemory chatMemory = createChatMemory();
        ChatWithMemory chatWithMemory = AiServices.builder(ChatWithMemory.class)
                .chatModel(createChatModel())
                .chatMemory(chatMemory)
                .build();

        /* **** First request **** */

        String systemMessage = "You are helpful assistant";
        String firstUserMessage = "Hello, my name is Klaus";

        mock.chat().reply("Nice to meet you Klaus");

        String firstAiMessage = chatWithMemory.chatWithSystemMessage(firstUserMessage);

        // assert response
        assertThat(firstAiMessage).isEqualTo("Nice to meet you Klaus");

        // assert request
        assertMultipleRequestMessage(mock.chat().singleRequest().bodyAsJson(),
                List.of(
                        new MessageContent("system", systemMessage),
                        new MessageContent("user", firstUserMessage)));

        // assert chat memory
        assertThat(chatMemory.messages()).hasSize(3)
                .extracting(ChatMessage::type, LangChain4jUtil::chatMessageToText)
                .containsExactly(tuple(SYSTEM, systemMessage), tuple(USER, firstUserMessage), tuple(AI, firstAiMessage));

        /* **** Second request **** */
        mock.reset();

        String secondUserMessage = "What is my name?";
        mock.chat().reply("Your name is Klaus");
        String secondAiMessage = chatWithMemory.chatWithSystemMessage(secondUserMessage);

        // assert response
        assertThat(secondAiMessage).contains("Klaus");

        // assert request
        assertMultipleRequestMessage(mock.chat().singleRequest().bodyAsJson(),
                List.of(
                        new MessageContent("system", systemMessage),
                        new MessageContent("user", firstUserMessage),
                        new MessageContent("assistant", firstAiMessage),
                        new MessageContent("user", secondUserMessage)));

        // assert chat memory
        assertThat(chatMemory.messages()).hasSize(5)
                .extracting(ChatMessage::type, LangChain4jUtil::chatMessageToText)
                .containsExactly(tuple(SYSTEM, systemMessage), tuple(USER, firstUserMessage), tuple(AI, firstAiMessage),
                        tuple(USER, secondUserMessage),
                        tuple(AI, secondAiMessage));
    }

    @Test
    void should_keep_chat_memory_and_add_new_system_message() {
        MessageWindowChatMemory chatMemory = createChatMemory();
        ChatWithMemory chatWithMemory = AiServices.builder(ChatWithMemory.class)
                .chatModel(createChatModel())
                .chatMemory(chatMemory)
                .build();

        /* **** First request **** */

        String firstSystemMessage = "You are helpful assistant";
        String firstUserMessage = "Hello, my name is Klaus";

        mock.chat().reply("Nice to meet you Klaus");

        String firstAiMessage = chatWithMemory.chatWithSystemMessage(firstUserMessage);

        // assert response
        assertThat(firstAiMessage).isEqualTo("Nice to meet you Klaus");

        // assert request
        assertMultipleRequestMessage(mock.chat().singleRequest().bodyAsJson(),
                List.of(
                        new MessageContent("system", firstSystemMessage),
                        new MessageContent("user", firstUserMessage)));

        // assert chat memory
        assertThat(chatMemory.messages()).hasSize(3)
                .extracting(ChatMessage::type, LangChain4jUtil::chatMessageToText)
                .containsExactly(tuple(SYSTEM, firstSystemMessage), tuple(USER, firstUserMessage), tuple(AI, firstAiMessage));

        /* **** Second request **** */
        mock.reset();

        String secondSystemMessage = "You are funny assistant";
        String secondUserMessage = "What is my name?";
        mock.chat().reply("Your name is Klaus");
        String secondAiMessage = chatWithMemory.chatWithAnotherSystemMessage(secondUserMessage);

        // assert response
        assertThat(secondAiMessage).contains("Klaus");

        // assert request
        assertMultipleRequestMessage(mock.chat().singleRequest().bodyAsJson(),
                List.of(
                        new MessageContent("system", secondSystemMessage),
                        new MessageContent("user", firstUserMessage),
                        new MessageContent("assistant", firstAiMessage),
                        new MessageContent("user", secondUserMessage)));

        // assert chat memory
        assertThat(chatMemory.messages()).hasSize(5)
                .extracting(ChatMessage::type, LangChain4jUtil::chatMessageToText)
                .containsExactly(tuple(SYSTEM, secondSystemMessage), tuple(USER, firstUserMessage), tuple(AI, firstAiMessage),
                        tuple(USER, secondUserMessage), tuple(AI, secondAiMessage));
    }

    interface ChatWithSeparateMemoryForEachUser {

        String chat(@MemoryId int memoryId, @UserMessage String userMessage);
    }

    @Test
    void should_keep_separate_chat_memory_for_each_user_in_store() {

        // emulating persistent storage
        Map</* memoryId */ Object, String> persistentStorage = new HashMap<>();

        ChatMemoryStore store = new ChatMemoryStore() {

            @Override
            public List<ChatMessage> getMessages(Object memoryId) {
                return messagesFromJson(persistentStorage.get(memoryId));
            }

            @Override
            public void updateMessages(Object memoryId, List<ChatMessage> messages) {
                persistentStorage.put(memoryId, messagesToJson(messages));
            }

            @Override
            public void deleteMessages(Object memoryId) {
                persistentStorage.remove(memoryId);
            }
        };

        ChatMemoryProvider chatMemoryProvider = memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(10)
                .chatMemoryStore(store)
                .build();

        int firstMemoryId = 1;
        int secondMemoryId = 2;

        ChatWithSeparateMemoryForEachUser chatWithMemory = AiServices.builder(ChatWithSeparateMemoryForEachUser.class)
                .chatModel(createChatModel())
                .chatMemoryProvider(chatMemoryProvider)
                .build();

        /* **** First request for user 1 **** */
        String firstMessageFromFirstUser = "Hello, my name is Klaus";
        mock.chat().reply("Nice to meet you Klaus");
        String firstAiResponseToFirstUser = chatWithMemory.chat(firstMemoryId, firstMessageFromFirstUser);

        // assert response
        assertThat(firstAiResponseToFirstUser).isEqualTo("Nice to meet you Klaus");

        // assert request
        assertSingleRequestMessage(mock.chat().singleRequest().bodyAsJson(), firstMessageFromFirstUser);

        // assert chat memory
        assertThat(store.getMessages(firstMemoryId)).hasSize(2)
                .extracting(ChatMessage::type, LangChain4jUtil::chatMessageToText)
                .containsExactly(tuple(USER, firstMessageFromFirstUser), tuple(AI, firstAiResponseToFirstUser));

        /* **** First request for user 2 **** */
        mock.reset();

        String firstMessageFromSecondUser = "Hello, my name is Francine";
        mock.chat().reply("Nice to meet you Francine");
        String firstAiResponseToSecondUser = chatWithMemory.chat(secondMemoryId, firstMessageFromSecondUser);

        // assert response
        assertThat(firstAiResponseToSecondUser).isEqualTo("Nice to meet you Francine");

        // assert request
        assertSingleRequestMessage(mock.chat().singleRequest().bodyAsJson(), firstMessageFromSecondUser);

        // assert chat memory
        assertThat(store.getMessages(secondMemoryId)).hasSize(2)
                .extracting(ChatMessage::type, LangChain4jUtil::chatMessageToText)
                .containsExactly(tuple(USER, firstMessageFromSecondUser), tuple(AI, firstAiResponseToSecondUser));

        /* **** Second request for user 1 **** */
        mock.reset();

        String secondsMessageFromFirstUser = "What is my name?";
        mock.chat().reply("Your name is Klaus");
        String secondAiMessageToFirstUser = chatWithMemory.chat(firstMemoryId, secondsMessageFromFirstUser);

        // assert response
        assertThat(secondAiMessageToFirstUser).contains("Klaus");

        // assert request
        assertMultipleRequestMessage(mock.chat().singleRequest().bodyAsJson(),
                List.of(
                        new MessageContent("user", firstMessageFromFirstUser),
                        new MessageContent("assistant", firstAiResponseToFirstUser),
                        new MessageContent("user", secondsMessageFromFirstUser)));

        // assert chat memory
        assertThat(store.getMessages(firstMemoryId)).hasSize(4)
                .extracting(ChatMessage::type, LangChain4jUtil::chatMessageToText)
                .containsExactly(tuple(USER, firstMessageFromFirstUser), tuple(AI, firstAiResponseToFirstUser),
                        tuple(USER, secondsMessageFromFirstUser), tuple(AI, secondAiMessageToFirstUser));

        /* **** Second request for user 2 **** */
        mock.reset();

        String secondsMessageFromSecondUser = "What is my name?";
        mock.chat().reply("Your name is Francine");
        String secondAiMessageToSecondUser = chatWithMemory.chat(secondMemoryId, secondsMessageFromSecondUser);

        // assert response
        assertThat(secondAiMessageToSecondUser).contains("Francine");

        // assert request
        assertMultipleRequestMessage(mock.chat().singleRequest().bodyAsJson(),
                List.of(
                        new MessageContent("user", firstMessageFromSecondUser),
                        new MessageContent("assistant", firstAiResponseToSecondUser),
                        new MessageContent("user", secondsMessageFromSecondUser)));

        // assert chat memory
        assertThat(store.getMessages(secondMemoryId)).hasSize(4)
                .extracting(ChatMessage::type, LangChain4jUtil::chatMessageToText)
                .containsExactly(tuple(USER, firstMessageFromSecondUser), tuple(AI, firstAiResponseToSecondUser),
                        tuple(USER, secondsMessageFromSecondUser), tuple(AI, secondAiMessageToSecondUser));
    }

    interface AssistantReturningResult {

        Result<String> chat(String userMessage);
    }

    @Test
    void should_return_result() {
        mock.chat().reply(ChatReply.text("Berlin is the capital of Germany").usage(9, 12));

        // given
        AssistantReturningResult assistant = AiServices.create(AssistantReturningResult.class, createChatModel());

        String userMessage = "What is the capital of Germany?";

        // when
        Result<String> result = assistant.chat(userMessage);

        // then
        assertThat(result.content()).containsIgnoringCase("Berlin");

        TokenUsage tokenUsage = result.tokenUsage();
        assertThat(tokenUsage).isNotNull();
        assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(result.sources()).isEmpty();

        assertSingleRequestMessage(mock.chat().singleRequest().bodyAsJson(), "What is the capital of Germany?");
    }

    interface AssistantReturningPrimitive {

        boolean chat(String userMessage);
    }

    @Test
    void should_return_primitive() {
        mock.chat().reply("true");

        // given
        AssistantReturningPrimitive assistant = AiServices.create(AssistantReturningPrimitive.class, createChatModel());

        String userMessage = "Is Berlin the is the capital of Germany?";

        // when
        boolean result = assistant.chat(userMessage);

        // then
        assertThat(result).isTrue();

        assertSingleRequestMessage(mock.chat().singleRequest().bodyAsJson(),
                "Is Berlin the is the capital of Germany?\nYou must answer strictly in the following format: one of [true, false]");
    }

    static class Calculator {

        private final Runnable after;

        Calculator(Runnable after) {
            this.after = after;
        }

        @Tool("calculates the square root of the provided number")
        double squareRoot(double number) {
            var result = Math.sqrt(number);
            after.run();
            return result;
        }
    }

    @Test
    void should_execute_tool_then_answer() {
        var firstResponse = """
                {
                  "id": "chatcmpl-8D88Dag1gAKnOPP9Ed4bos7vSpaNz",
                  "object": "chat.completion",
                  "created": 1698140213,
                  "model": "gpt-3.5-turbo-0613",
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": null,
                        "function_call": {
                          "name": "squareRoot",
                          "arguments": "{\\n  \\"number\\": 485906798473894056\\n}"
                        }
                      },
                      "finish_reason": "function_call"
                    }
                  ],
                  "usage": {
                    "prompt_tokens": 65,
                    "completion_tokens": 20,
                    "total_tokens": 85
                  }
                }
                """;

        var secondResponse = """
                        {
                          "id": "chatcmpl-8D88FIAUWSpwLaShFr0w8G1SWuVdl",
                          "object": "chat.completion",
                          "created": 1698140215,
                          "model": "gpt-3.5-turbo-0613",
                          "choices": [
                            {
                              "index": 0,
                              "message": {
                                "role": "assistant",
                                "content": "The square root of 485,906,798,473,894,056 in scientific notation is approximately 6.97070153193991E8."
                              },
                              "finish_reason": "stop"
                            }
                          ],
                          "usage": {
                            "prompt_tokens": 102,
                            "completion_tokens": 33,
                            "total_tokens": 135
                          }
                        }
                """;

        // the fixtures use the legacy function_call format, which the neutral model cannot express
        mock.raw().on("POST", "/v1/chat/completions").replies(
                RawResponse.json(200, firstResponse),
                RawResponse.json(200, secondResponse));

        AtomicInteger toolExecutions = new AtomicInteger();
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(createChatModel())
                .chatMemory(createChatMemory())
                .tools(new Calculator(toolExecutions::incrementAndGet))
                .build();

        String userMessage = "What is the square root of 485906798473894056 in scientific notation?";

        String answer = assistant.chat(userMessage);

        assertThat(answer).isEqualTo(
                "The square root of 485,906,798,473,894,056 in scientific notation is approximately 6.97070153193991E8.");

        assertThat(toolExecutions).hasValue(1);

        List<CapturedRequest> requests = mock.requests();
        assertThat(requests).hasSize(2);
        assertThat(requests).allSatisfy(request -> {
            assertThat(request.path()).isEqualTo("/v1/chat/completions");
            assertThat(request.query()).isNull();
            assertThat(request.header("Authorization")).isEqualTo("Bearer whatever");
        });

        assertSingleRequestMessage(requests.get(0).bodyAsJson(),
                "What is the square root of 485906798473894056 in scientific notation?");
        assertMultipleRequestMessage(requests.get(1).bodyAsJson(),
                List.of(
                        new MessageContent("user", "What is the square root of 485906798473894056 in scientific notation?"),
                        new MessageContent("assistant", null),
                        new MessageContent("function", "6.97070153193991E8")));
    }
}
