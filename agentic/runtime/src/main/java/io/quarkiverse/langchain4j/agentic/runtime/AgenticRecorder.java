package io.quarkiverse.langchain4j.agentic.runtime;

import java.util.Collections;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.util.TypeLiteral;

import org.jboss.logging.Logger;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.tool.ToolProvider;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.annotations.Recorder;
import io.quarkus.runtime.annotations.RuntimeInit;
import io.quarkus.runtime.annotations.StaticInit;

@Recorder
public class AgenticRecorder {

    private static final Logger log = Logger.getLogger(AgenticRecorder.class);
    private static Set<String> agentsWithMcpToolBox = Collections.emptySet();

    @StaticInit
    public void setAgentsWithMcpToolBox(Set<String> agentsWithMcpToolBox) {
        AgenticRecorder.agentsWithMcpToolBox = Collections.unmodifiableSet(agentsWithMcpToolBox);
    }

    @RuntimeInit
    public Function<SyntheticCreationalContext<Object>, Object> createAiAgent(AiAgentCreateInfo info) {
        return new Function<>() {
            @Override
            public Object apply(SyntheticCreationalContext<Object> cdiContext) {
                ChatModel chatModel;
                if (info.chatModelInfo() instanceof AiAgentCreateInfo.ChatModelInfo.FromAnnotation) {
                    chatModel = null;
                } else if (info.chatModelInfo() instanceof AiAgentCreateInfo.ChatModelInfo.FromBeanWithName b) {
                    if (NamedConfigUtil.isDefault(b.name())) {
                        chatModel = cdiContext.getInjectedReference(ChatModel.class);
                    } else {
                        chatModel = cdiContext.getInjectedReference(ChatModel.class, ModelName.Literal.of(b.name()));
                    }
                } else {
                    throw new IllegalStateException("Unknown type: " + info.chatModelInfo().getClass());
                }

                return AgenticServices.createAgenticSystem(loadClassSafe(info), chatModel,
                        new QuarkusAgenticContextConsumer(cdiContext, info));
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

    private record QuarkusAgenticContextConsumer(SyntheticCreationalContext<Object> cdiContext,
            AiAgentCreateInfo aiAgentCreateInfo)
            implements
                Consumer<AgenticServices.DeclarativeAgentCreationContext> {

        private static final TypeLiteral<Instance<ToolProvider>> TOOL_PROVIDER_TYPE_LITERAL = new TypeLiteral<>() {
        };

        @Override
        public void accept(AgenticServices.DeclarativeAgentCreationContext agenticContext) {
            if (AgenticRecorder.agentsWithMcpToolBox.contains(agenticContext.agentServiceClass().getName())) {
                Instance<ToolProvider> injectedReference = cdiContext.getInjectedReference(TOOL_PROVIDER_TYPE_LITERAL);
                if (injectedReference.isResolvable()) {
                    agenticContext.agentBuilder().toolProvider(injectedReference.get());
                }
            }
        }
    }

    private static final class NoOpConsumer implements Consumer {

        private static final NoOpConsumer INSTANCE = new NoOpConsumer();

        @Override
        public void accept(Object t) {

        }
    }
}
