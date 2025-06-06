package io.quarkiverse.langchain4j.agentic;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.output.ServiceOutputParser;
import io.quarkiverse.langchain4j.runtime.QuarkusServiceOutputParser;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import picocli.CommandLine;

@CommandLine.Command(name = "star", description = "Gets news star news about the person")
public class StarNewsFinder implements Runnable {

    private static final ServiceOutputParser SERVICE_OUTPUT_PARSER = new QuarkusServiceOutputParser();


    @CommandLine.Parameters(description = "input", defaultValue = "My name is George and my astrological sign is Aquarius")
    private String input;

    private final ChatModel chatModel;

    public StarNewsFinder(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public void run() {
        Map<String, Object> state = new HashMap<>();

        final var rootSystemPrompt =
                """
                You are an agent that is an expert in astrology.
                Your job is to use the person's name and star sign to write up a
                something amusing for the target person.
                
                Begin by summarizing their horoscope in a concise, amusing way, then
                talk about the news. End with a surprising signoff.
                """;

        final var starNewsTool = ToolSpecification.builder()
                .name("starNews")
                .description("Finds the ")
                .parameters(JsonObjectSchema.builder().addStringProperty("input").required("input").build())
                .build();

        final var askForInputTool = ToolSpecification.builder()
                .name("askForInput")
                .description("Asks the user provide additional input that is needed by some part of the agent")
                .parameters(JsonObjectSchema.builder().addStringProperty("input").required("input").build())
                .build();

        final var extractorSystemPrompt =
                """
                You are an agent that specializes in extracting structured information from a conversation.
                """;
        final var extractorUserPromptTemplate =
                """
                The conversion is the following:
                
                ---
                %s
                ---
                
                %s
                """;
        var extractorAgent = chatModel;
        ChatResponse extractorChatResponse = extractorAgent.chat(ChatRequest.builder()
                .messages(SystemMessage.systemMessage(extractorSystemPrompt),
                        UserMessage.from(String.format(extractorUserPromptTemplate, input, SERVICE_OUTPUT_PARSER.outputFormatInstructions(Person.class))))
                        .parameters(ChatRequestParameters.builder().modelName("gpt-4o").toolSpecifications(askForInputTool).build())
                .build());

        if (extractorChatResponse.aiMessage().hasToolExecutionRequests()) {
            System.out.println("Need to execute tool(s): " + extractorChatResponse.aiMessage().toolExecutionRequests());
        } else {
            Person person = (Person) SERVICE_OUTPUT_PARSER.parse(extractorChatResponse, Person.class);
            state.put("person", person);


        }

    }

    private record Person(String name, String sign) {

    }
}
