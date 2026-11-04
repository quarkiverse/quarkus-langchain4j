package io.quarkiverse.langchain4j.agentic.runtime;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.decision.DecisionModel;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.arc.Arc;
import io.quarkus.arc.SyntheticCreationalContext;

/**
 * @param decisionModelInfo how the decision model of a decision agent is obtained, {@code null} for the other agents
 * @param perInstanceScope whether the scope of the agent bean is not the application scope, so that each bean
 *        instance has its own memory id, and its agentic scope and chat memories are evicted with it
 */
public record AiAgentCreateInfo(String agentClassName, ChatModelInfo chatModelInfo, boolean hasInterceptorBindings,
        boolean hasMcpToolBox, DecisionModelInfo decisionModelInfo, boolean perInstanceScope) {

    public boolean isDecisionAgent() {
        return decisionModelInfo != null;
    }

    public sealed interface ChatModelInfo
            permits ChatModelInfo.FromAnnotation, ChatModelInfo.FromBeanWithName, ChatModelInfo.NotNeeded {

        /**
         * Resolves the model from the injected references of the synthetic bean of an agent interface.
         */
        ChatModel resolve(SyntheticCreationalContext<?> cdiContext);

        /**
         * Resolves the model from the container, for a class-based agent.
         */
        ChatModel resolve();

        final class FromAnnotation implements ChatModelInfo {
            @Override
            public ChatModel resolve(SyntheticCreationalContext<?> cdiContext) {
                return null;
            }

            @Override
            public ChatModel resolve() {
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

            @Override
            public ChatModel resolve() {
                if (NamedConfigUtil.isDefault(name)) {
                    return Arc.container().instance(ChatModel.class).get();
                }
                return Arc.container().instance(ChatModel.class, ModelName.Literal.of(name)).get();
            }
        }

        final class NotNeeded implements ChatModelInfo {
            @Override
            public ChatModel resolve(SyntheticCreationalContext<?> cdiContext) {
                return resolve();
            }

            @Override
            public ChatModel resolve() {
                var handle = Arc.container().instance(ChatModel.class);
                return handle.isAvailable() ? handle.get() : null;
            }
        }
    }

    public sealed interface DecisionModelInfo
            permits DecisionModelInfo.FromAnnotation, DecisionModelInfo.FromBeanWithName {

        DecisionModel resolve(SyntheticCreationalContext<?> cdiContext);

        DecisionModel resolve();

        /**
         * The model is provided by the {@code @DecisionModelSupplier} method of the agent.
         */
        final class FromAnnotation implements DecisionModelInfo {
            @Override
            public DecisionModel resolve(SyntheticCreationalContext<?> cdiContext) {
                return null;
            }

            @Override
            public DecisionModel resolve() {
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

            @Override
            public DecisionModel resolve() {
                if (NamedConfigUtil.isDefault(name)) {
                    return Arc.container().instance(DecisionModel.class).get();
                }
                return Arc.container().instance(DecisionModel.class, ModelName.Literal.of(name)).get();
            }
        }
    }
}
