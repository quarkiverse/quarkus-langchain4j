package io.quarkiverse.langchain4j.agentic;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import picocli.CommandLine;

@CommandLine.Command(name = "write", description = "Write a story")
public class WriteCommand implements Runnable {

    @CommandLine.Parameters(description = "Topic", defaultValue = "a brave kitten exploring a haunted house")
    private String topic;

    private final ChatModel chatModel;

    public WriteCommand(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @SuppressWarnings("UnnecessaryLocalVariable")
    @Override
    public void run() {
        Map<String, Object> state = new HashMap<>();

        var storyGenerator = chatModel;
        ChatResponse generatorResponse = storyGenerator.chat(SystemMessage.systemMessage(
                        "You are a story writer. Write a short story (around 100 words) about a cat based on the the topic provided by the user"),
                UserMessage.from(topic));

        state.put("currentStory" ,generatorResponse.aiMessage().text());

        final int MAX_ITERATIONS = 5;
        int i = 0;
        while(i < MAX_ITERATIONS) {
            var critic = chatModel;
            ChatRequest criticRequest = ChatRequest.builder()
                    .messages(SystemMessage.systemMessage(
                            """
                            You are a story critic. Provide 1-2 sentences of constructive criticism of the provided story and suggest how to improve it. Mainly focus on plot and character.
                            If the story is good enough, then call the 'exitLoop' function and don't output any criticism.
                            """),
                            UserMessage.from((String) state.get("currentStory")))
                    .toolSpecifications(ToolSpecification.builder().description("Exit Loop").name("exitLoop").build())
                    .build();
            ChatResponse criticResponse = critic.chat(criticRequest);
            state.put("criticism" ,criticResponse.aiMessage().text());
            if (criticResponse.aiMessage().hasToolExecutionRequests()) {
                if (criticResponse.aiMessage().toolExecutionRequests().stream().allMatch(tr -> tr.name().equals("exitLoop"))) {
                    break;
                }
                Set<String> unknownTools = criticResponse.aiMessage().toolExecutionRequests().stream().map(
                        ToolExecutionRequest::name).filter(name -> !name.equals("exitLoop")).collect(
                        Collectors.toSet());
                throw new IllegalStateException("Unknown tool execution requests: " + String.join(",", unknownTools));
            }

            var reviser = chatModel;
            ChatResponse reviserResponse = reviser.chat(
                    SystemMessage.systemMessage("You are a story reviser. Revise the story provided based on the provided criticism. ONLY output only the revised story"),
                    UserMessage.from(String.format("""
                                **Story**:
                                
                                %s
                                
                                ** Criticism**:
                                
                                %s
                                """, state.get("currentStory"), state.get("criticism"))));
            state.put("currentStory" ,reviserResponse.aiMessage().text());
            i++;
        }


        System.out.println(state.get("currentStory"));
    }
}
