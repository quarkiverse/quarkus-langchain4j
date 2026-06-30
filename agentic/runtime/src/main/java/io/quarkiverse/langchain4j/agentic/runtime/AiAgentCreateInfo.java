package io.quarkiverse.langchain4j.agentic.runtime;

import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.arc.Arc;
import io.quarkus.arc.SyntheticCreationalContext;

public record AiAgentCreateInfo(String agentClassName, ChatModelInfo chatModelInfo, boolean hasInterceptorBindings,
        boolean hasMcpToolBox) {

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
}
