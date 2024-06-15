package io.quarkiverse.langchain4j.runtime.aiservice;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jboss.logging.Logger;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;

public class DynamicVariableHandler {

    private static final Logger log = Logger.getLogger(DynamicVariableHandler.class);

    static Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\((.*?)\\)");

    Map<String, String> variables = new HashMap<>();

    public void addVariable(String var, String value) {
        variables.put(var, value);
    }

    public AiMessage substituteAssistantArguments(AiMessage message) {
        if (message.text() == null) {
            return message;
        }
        return AiMessage.from(substituteArguments(message.text(), variables));
    }

    public ToolExecutionRequest substituteArguments(ToolExecutionRequest toolExecutionRequest) {
        return ToolExecutionRequest.builder()
                .id(toolExecutionRequest.id())
                .name(toolExecutionRequest.name())
                .arguments(substituteArguments(toolExecutionRequest.arguments(), variables)).build();
    }

    private static String substituteArguments(String msg, Map<String, String> resultMap) {
        Matcher matcher = VARIABLE_PATTERN.matcher(msg);
        StringBuilder newArguments = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String replacement = resultMap.getOrDefault(key, matcher.group(0));
            log.debugv("Replace argument of {0} by {1}", key, replacement);

            matcher.appendReplacement(newArguments, replacement);
        }
        matcher.appendTail(newArguments);
        return newArguments.toString();
    }
}
