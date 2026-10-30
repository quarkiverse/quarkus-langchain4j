package io.quarkiverse.langchain4j.agentic.runtime;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.decision.DecisionModel;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.arc.Arc;
import io.quarkus.arc.SyntheticCreationalContext;

/**
 * @param decisionModelInfo how the decision model of a decision agent is obtained, {@code null} for the other agents
 */
public record AiAgentCreateInfo(String agentClassName, ChatModelInfo chatModelInfo, boolean hasInterceptorBindings,
        boolean hasMcpToolBox, DecisionModelInfo decisionModelInfo) {

    public boolean isDecisionAgent() {
        return decisionModelInfo != null;
    }

    public sealed interface ChatModelInfo
            permits ChatModelInfo.FromAnnotation, ChatModelInfo.FromBeanWithName, ChatModelInfo.NotNeeded {

        ChatModel resolve(SyntheticCreationalContext<?> cdiContext);

        final class FromAnnotation implements ChatModelInfo {
            @Override
            public ChatModel resolve(SyntheticCreationalContext<?> cdiContext) {
                return null;
            }
        }

        record FromBeanWithName(String name) implements ChatModelInfo {
            @Override
            public ChatModel resolve(SyntheticCreationalContext<?> cdiContext) {
                if (NamedConfigUtil.isDefault(name)) {
                    return cdiContext.getInjectedReference(ChatModel.class);
                }
                return cdiContext.getInjectedReference(ChatModel.class, ModelName.Literal.of(name));
            }
        }

        final class NotNeeded implements ChatModelInfo {
            @Override
            public ChatModel resolve(SyntheticCreationalContext<?> cdiContext) {
                var handle = Arc.container().instance(ChatModel.class);
                return handle.isAvailable() ? handle.get() : null;
            }
        }
    }

    public sealed interface DecisionModelInfo
            permits DecisionModelInfo.FromAnnotation, DecisionModelInfo.FromBeanWithName {

        DecisionModel resolve(SyntheticCreationalContext<?> cdiContext);

        /**
         * The model is provided by the {@code @DecisionModelSupplier} method of the agent.
         */
        final class FromAnnotation implements DecisionModelInfo {
            @Override
            public DecisionModel resolve(SyntheticCreationalContext<?> cdiContext) {
                return null;
            }
        }

        record FromBeanWithName(String name) implements DecisionModelInfo {
            @Override
            public DecisionModel resolve(SyntheticCreationalContext<?> cdiContext) {
                if (NamedConfigUtil.isDefault(name)) {
                    return cdiContext.getInjectedReference(DecisionModel.class);
                }
                return cdiContext.getInjectedReference(DecisionModel.class, ModelName.Literal.of(name));
            }
        }
    }
}
