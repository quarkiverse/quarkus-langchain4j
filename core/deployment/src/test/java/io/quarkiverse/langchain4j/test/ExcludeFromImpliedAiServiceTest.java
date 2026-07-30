package io.quarkiverse.langchain4j.test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.Consumer;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.deployment.ExcludeFromImpliedAiServiceBuildItem;
import io.quarkus.builder.BuildChainBuilder;
import io.quarkus.builder.BuildContext;
import io.quarkus.builder.BuildStep;
import io.quarkus.test.QuarkusUnitTest;

public class ExcludeFromImpliedAiServiceTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(ImpliedService.class, ExcludedService.class, MyLanguageModel.class))
            .addBuildChainCustomizer(buildCustomizer());

    protected static Consumer<BuildChainBuilder> buildCustomizer() {
        return builder -> builder.addBuildStep(new BuildStep() {
            @Override
            public void execute(BuildContext context) {
                context.produce(new ExcludeFromImpliedAiServiceBuildItem(ExcludedService.class.getName()));
            }
        })
                .produces(ExcludeFromImpliedAiServiceBuildItem.class)
                .build();
    }

    @Inject
    Instance<ImpliedService> impliedServiceInstance;

    @Inject
    Instance<ExcludedService> excludedServiceInstance;

    interface ImpliedService {
        @UserMessage("Hello {name}")
        String greet(String name);
    }

    interface ExcludedService {
        @UserMessage("Hello {name}")
        String greet(String name);
    }

    @Singleton
    public static class MyLanguageModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest request) {
            return null;
        }
    }

    @Test
    void impliedServiceIsRegistered() {
        assertTrue(impliedServiceInstance.isResolvable(),
                "ImpliedService should be auto-registered as an AI service");
    }

    @Test
    void excludedServiceIsNotRegistered() {
        assertFalse(excludedServiceInstance.isResolvable(),
                "ExcludedService should not be registered as an AI service");
    }
}
