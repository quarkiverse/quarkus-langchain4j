package io.quarkiverse.langchain4j.runtime.aiservice;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import io.quarkiverse.langchain4j.data.AiStatsMessage;

/**
 * This class associate the ToolExecutionResul to the associated variable
 * identified by @{@link ToolExecutionRequest#id()}.
 * It will use them after when tools inputs @{@link ToolExecutionRequest#arguments()} are based
 * on previous result variable and when AiMessage text contains variables too.
 * See usage in @{@link AiServiceMethodImplementationSupport}
 */
@Experimental
public class VariableHandler {

    static Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\((.*?)\\)");

    Map<String, String> variables = new HashMap<>();

    public void addVariable(String var, String value) {
        variables.put(var, value);
    }

    public AiMessage substituteVariables(AiMessage message) {
        if (message.text() == null) {
            return message;
        }
        if (message instanceof AiStatsMessage updatableMessage) {
            updatableMessage.updateText(substituteVariables(message.text(), variables));
            return updatableMessage;
        }
        return message;
    }

    public ToolExecutionRequest substituteVariables(ToolExecutionRequest toolExecutionRequest) {
        return ToolExecutionRequest.builder()
                .id(toolExecutionRequest.id())
                .name(toolExecutionRequest.name())
                .arguments(substituteVariables(toolExecutionRequest.arguments(), variables)).build();
    }

    private static String substituteVariables(String msg, Map<String, String> resultMap) {
        Matcher matcher = VARIABLE_PATTERN.matcher(msg);
        StringBuilder newArguments = new StringBuilder();
        if (!matcher.find()) {
            return msg;
        }
        do {
            String key = matcher.group(1);
            String replacement = resultMap.getOrDefault(key, matcher.group(0));
            matcher.appendReplacement(newArguments, replacement);
        } while (matcher.find());
        matcher.appendTail(newArguments);
        return newArguments.toString();
    }
}
