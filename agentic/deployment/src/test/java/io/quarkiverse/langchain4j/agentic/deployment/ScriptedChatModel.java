package io.quarkiverse.langchain4j.agentic.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * Records every request and answers "ok". An agent with tools first calls its tool, and the supervisor planner
 * invokes each of its agents once, in order, with the topic of the current request, before finishing.
 */
public class ScriptedChatModel implements ChatModel {

    private static final List<ChatRequest> REQUESTS = new CopyOnWriteArrayList<>();

    private static final Pattern TOPIC = Pattern.compile("topic (\\w+)");
    private static final Pattern PLANNER_AGENT_NAME = Pattern.compile("\\{'([^']+)'");
    private static final Pattern PLANNER_REQUEST = Pattern.compile("The user request is: '([^']*)'");

    @Override
    public ChatResponse doChat(ChatRequest request) {
        REQUESTS.add(request);
        List<ChatMessage> messages = request.messages();
        ChatMessage last = messages.get(messages.size() - 1);
        String system = messages.get(0) instanceof SystemMessage systemMessage ? systemMessage.text() : "";

        if (system.startsWith("You are a planner expert")) {
            return reply(AiMessage.from(nextPlannerStep(system, messages)));
        }
        if (!request.toolSpecifications().isEmpty() && last instanceof UserMessage user) {
            Matcher topic = TOPIC.matcher(user.singleText());
            topic.find();
            return reply(AiMessage.from(ToolExecutionRequest.builder()
                    .id("1")
                    .name("lookup")
                    .arguments("{\"topic\":\"" + topic.group(1) + "\"}")
                    .build()));
        }
        return reply(AiMessage.from("ok"));
    }

    public static void clearRequests() {
        REQUESTS.clear();
    }

    /**
     * The conversations sent to the model by the given agent, in order. A request belongs to the agent whose prompt
     * is its last user message.
     */
    public static List<List<String>> conversationsOf(String agent) {
        List<List<String>> conversations = new ArrayList<>();
        for (ChatRequest request : REQUESTS) {
            List<ChatMessage> messages = request.messages();
            for (int i = messages.size() - 1; i >= 0; i--) {
                if (messages.get(i) instanceof UserMessage user) {
                    if (user.singleText().startsWith(agent + " agent")) {
                        conversations.add(conversation(request));
                    }
                    break;
                }
            }
        }
        return conversations;
    }

    public static List<String> lastConversationOf(String agent) {
        List<List<String>> conversations = conversationsOf(agent);
        assertThat(conversations).as("requests sent by " + agent).isNotEmpty();
        return conversations.get(conversations.size() - 1);
    }

    private static List<String> conversation(ChatRequest request) {
        List<String> conversation = new ArrayList<>();
        for (ChatMessage message : request.messages()) {
            if (!(message instanceof SystemMessage)) {
                conversation.add(message.type() + ": " + text(message));
            }
        }
        return conversation;
    }

    private static String text(ChatMessage message) {
        if (message instanceof UserMessage user) {
            return user.singleText();
        }
        if (message instanceof AiMessage ai && ai.hasToolExecutionRequests()) {
            return "calls " + ai.toolExecutionRequests().get(0).name();
        }
        if (message instanceof AiMessage ai) {
            return ai.text();
        }
        if (message instanceof ToolExecutionResultMessage result) {
            return result.text();
        }
        return message.toString();
    }

    private static String nextPlannerStep(String system, List<ChatMessage> messages) {
        List<String> agentNames = new ArrayList<>();
        Matcher names = PLANNER_AGENT_NAME.matcher(system.substring(system.indexOf("available agents is:")));
        while (names.find()) {
            agentNames.add(names.group(1));
        }
        Matcher lastRequest = PLANNER_REQUEST.matcher(text(messages.get(messages.size() - 1)));
        lastRequest.find();
        String topic = lastRequest.group(1);

        int plannedSteps = 0;
        boolean currentRequest = false;
        for (ChatMessage message : messages) {
            if (message instanceof UserMessage user && user.singleText().contains("'" + topic + "'")) {
                currentRequest = true;
            }
            if (currentRequest && message instanceof AiMessage) {
                plannedSteps++;
            }
        }
        if (plannedSteps < agentNames.size()) {
            return "{\"agentName\":\"" + agentNames.get(plannedSteps) + "\",\"arguments\":{\"topic\":\"" + topic + "\"}}";
        }
        return "{\"agentName\":\"done\",\"arguments\":{\"response\":\"ok\"}}";
    }

    private static ChatResponse reply(AiMessage message) {
        return ChatResponse.builder().aiMessage(message).build();
    }
}
