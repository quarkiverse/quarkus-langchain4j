package io.quarkiverse.langchain4j.test;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.assertj.core.api.Assertions;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.quarkus.test.QuarkusUnitTest;

public class NoChatLanguageProviderButCustomBeanTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class));

    @Inject
    ChatLanguageModel chatLanguageModel;

    @Test
    void test() {
        Assertions.assertThat(chatLanguageModel).isInstanceOf(CustomChatLanguageModel.class);
    }

    @Singleton
    public static class CustomChatLanguageModel implements ChatLanguageModel {

        @Override
        public ChatResponse doChat(ChatRequest request) {
            return null;
        }
    }
}
