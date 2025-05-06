package io.quarkiverse.langchain4j.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.QuarkusUnitTest;

public class NamedAiServicesAreResolvableByNameTest {

    private static final String MY_NAMED_SERVICE_BEAN = "myNamedServiceBean";

    @Inject
    BeanManager beanManager;

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(MyNamedService.class));

    @Named(MY_NAMED_SERVICE_BEAN)
    @RegisterAiService(chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
    interface MyNamedService {
        @UserMessage("Dummy prompt for " + MY_NAMED_SERVICE_BEAN)
        String chat();
    }

    @Singleton
    public static class MyLanguageModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest request) {
            return null;
        }
    }

    @Test
    void namedAiServiceCouldBeResolvedByNameTest() {
        Set<Bean<?>> beans = beanManager.getBeans(MY_NAMED_SERVICE_BEAN);
        assertEquals(1, beans.size());
        Bean<?> bean = beans.iterator().next();
        assertNotNull(bean);
        assertTrue(MyNamedService.class.isAssignableFrom(bean.getBeanClass()));
    }
}
