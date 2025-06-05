package io.quarkiverse.langchain4j.agentic;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import picocli.CommandLine;

@CommandLine.Command(name = "weather", description = "Gives the weather")
public class WeatherCommand implements Runnable {

    @CommandLine.Parameters(description = "input", defaultValue = "What is the weather like in Athens?")
    private String input;

    private final ChatModel chatModel;

    public WeatherCommand(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @SuppressWarnings("UnnecessaryLocalVariable")
    @Override
    public void run() {
        final String mainSystemPrompt =
                """
                        You are the main Weather Agent coordinating a team. Your primary responsibility is to provide weather information for the given city.
                        Use the 'getWeather' tool ONLY for specific weather requests (e.g., 'weather in London').
                        Use the team of agents if the query matches their capabilities.
                        For anything else, respond appropriately or state you cannot handle it.
                        """;
        final ToolSpecification getWeatherTool = ToolSpecification.builder()
                .name("getWeather")
                .description("Get the weather of the given city")
                .parameters(JsonObjectSchema.builder().addStringProperty("city").required("city").build())
                .build();
        final ToolSpecification transferToAgentTool = ToolSpecification.builder()
                .name("transferToAgent")
                .description("Transfer control to another agent")
                .parameters(JsonObjectSchema.builder().addStringProperty("agentName").required("agentName").build())
                .build();

        var weatherAgent = chatModel;
        String effectiveSystemPrompt = mainSystemPrompt + "\n\n" + transferAgentsSystemPromptPart(
                List.of(new AgentSpec("greetingAgent", "Handles simple greetings and hellos"),
                        new AgentSpec("farewellAgent", "Handles simple farewells and goodbyes")));

        ChatRequest chatRequest = ChatRequest.builder()
                .toolSpecifications(getWeatherTool, transferToAgentTool)
                .messages(SystemMessage.systemMessage(effectiveSystemPrompt), UserMessage.from(input))
                .build();
        ChatResponse chatResponse = weatherAgent.chat(chatRequest);
        if (chatResponse.aiMessage().hasToolExecutionRequests()) {
            if (chatResponse.aiMessage().toolExecutionRequests().stream()
                    .allMatch(tr -> tr.name().equals("transferToAgent"))) {
                System.out.println("Now we need to transfer control to agent specified by args: " + chatResponse.aiMessage().toolExecutionRequests().get(0).arguments());
            } else if (chatResponse.aiMessage().toolExecutionRequests().stream()
                    .allMatch(tr -> tr.name().equals("getWeather"))) {
                System.out.println("Now we need to call the getWeather tool with these args: " + chatResponse.aiMessage().toolExecutionRequests().get(0).arguments());
            }  else {
                throw new IllegalStateException("Unknown tool execution request");
            }
        } else {
            System.out.println(chatResponse.aiMessage().text());
        }
    }

    private String transferAgentsSystemPromptPart(List<AgentSpec> specs) {
        StringBuilder sb = new StringBuilder("You have a list of other agents to transfer to:\n");
        for (AgentSpec spec : specs) {
            sb.append("\nAgent name: ").append(spec.name()).append("\n");
        }
        return sb.append("""
                 If you are the best to answer the question according to your description, you can answer it.
                 If another agent is better for answering the question according to its description, call `transferToAgent` function to transfer the question to that agent.
                 When transferring, do not generate any text other than the function call
                """).toString();
    }

    private record AgentSpec(String name, String description) {

    }
}
