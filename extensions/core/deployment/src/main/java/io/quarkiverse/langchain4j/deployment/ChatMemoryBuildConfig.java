package io.quarkiverse.langchain4j.deployment;

import static io.quarkus.runtime.annotations.ConfigPhase.BUILD_TIME;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = BUILD_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.chat-memory")
public interface ChatMemoryBuildConfig {

    /**
     * Configure the type of {@link ChatMemory} that will be used by default by the default {@link ChatMemoryProvider} bean.
     * <p>
     * The extension provides a default bean that configures {@link ChatMemoryProvider} for use with AI services
     * registered with {@link RegisterAiService}. This bean depends uses the {@code quarkus.langchain4j.chat-memory}
     * configuration to set things up while also depending on the presence of a bean of type {@link ChatMemoryStore} (for which
     * the extension also provides a default in the form of {@link InMemoryChatMemoryStore}).
     * <p>
     * If {@code token-window} is used, then the application must also provide a bean of type {@link Tokenizer}.
     * <p>
     * Users can choose to provide their own {@link ChatMemoryStore} bean or even their own {@link ChatMemoryProvider} bean
     * if full control over the details is needed.
     */
    @WithDefault("MESSAGE_WINDOW")
    Type type();

    enum Type {
        MESSAGE_WINDOW,
        TOKEN_WINDOW
    }

}
