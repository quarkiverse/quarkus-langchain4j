package io.quarkiverse.langchain4j.agentic.runtime;

import java.util.function.Function;

import org.jboss.logging.Logger;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class AgenticRecorder {

    private static final Logger log = Logger.getLogger(AgenticRecorder.class);

    public Function<SyntheticCreationalContext<Object>, Object> createAiAgent(AiAgentCreateInfo info) {
        return new Function<>() {
            @Override
            public Object apply(SyntheticCreationalContext<Object> context) {
                ChatModel chatModel;
                if (NamedConfigUtil.isDefault(info.chatModelName())) {
                    chatModel = context.getInjectedReference(ChatModel.class);
                } else {
                    chatModel = context.getInjectedReference(ChatModel.class, ModelName.Literal.of(info.chatModelName()));
                }
                return AgenticServices.createAgenticSystem(loadClassSafe(info), chatModel);
            }
        };
    }

    private static Class<?> loadClassSafe(AiAgentCreateInfo info) {
        try {
            return Class.forName(info.agentClassName(), true, Thread.currentThread().getContextClassLoader());
        } catch (ClassNotFoundException e) {
            log.error("Unable to load agent class '" + info.agentClassName() + "'", e);
            throw new RuntimeException(e);
        }
    }
}
