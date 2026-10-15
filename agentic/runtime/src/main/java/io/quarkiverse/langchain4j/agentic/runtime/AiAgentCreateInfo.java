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
                try {
                    if (NamedConfigUtil.isDefault(name)) {
                        return cdiContext.getInjectedReference(ChatModel.class);
                    }
                    return cdiContext.getInjectedReference(ChatModel.class, ModelName.Literal.of(name));
                } catch (IllegalArgumentException e) {
                    // Arc resolves the type of synthetic injection points with the context classloader of the
                    // thread creating the bean, so the lookup above can fail when an agent bean is created
                    // from a thread with a different context classloader; fall back to a programmatic lookup
                    if (NamedConfigUtil.isDefault(name)) {
                        return Arc.container().instance(ChatModel.class).get();
                    }
                    return Arc.container().instance(ChatModel.class, ModelName.Literal.of(name)).get();
                }
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
