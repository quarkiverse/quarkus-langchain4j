package io.quarkiverse.langchain4j.test.listeners;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.List;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.UserMessage;
import io.opentelemetry.api.trace.Span;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.runtime.listeners.ChatModelSpanContributor;
import io.quarkiverse.langchain4j.runtime.listeners.SpanChatModelListener;
import io.quarkus.maven.dependency.Dependency;
import io.quarkus.test.QuarkusUnitTest;

class ListenersProcessorChatModelSpanContributorTest {
    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class)
                            .addClasses(AiService.class, EchoChatLanguageModelSupplier.class,
                                    TestChatModelSpanContributor.class))
            .setForcedDependencies(List.of(Dependency.of("io.quarkus", "quarkus-opentelemetry", "3.15.2")));

    @Inject
    AiService aiService;
    @Inject
    SpanChatModelListener spanChatModelListener;
    @Inject
    ChatModelSpanContributor contributor;

    @Test
    void shouldHaveAllBeans() {
        assertThat(aiService).isNotNull();
        assertThat(spanChatModelListener).isNotNull();
        assertThat(contributor).isNotNull();
    }

    @Test
    void shouldHaveCustomImplementationProvidedByProject() {
        var requestContext = mock(ChatModelRequestContext.class);
        var responseContext = mock(ChatModelResponseContext.class);
        var errorContext = mock(ChatModelErrorContext.class);
        var currentSpan = mock(Span.class);

        contributor.onRequest(requestContext, currentSpan);
        contributor.onResponse(responseContext, currentSpan);
        contributor.onError(errorContext, currentSpan);

        verify(currentSpan).setAttribute("--custom-on-request--", "--value-on-request--");
        verify(currentSpan).setAttribute("--custom-on-response--", "--value-on-response--");
        verify(currentSpan).setAttribute("--custom-on-error--", "--value-on-error--");
        verifyNoMoreInteractions(requestContext, currentSpan);
    }

    @ApplicationScoped
    public static class TestChatModelSpanContributor implements ChatModelSpanContributor {
        @Override
        public void onRequest(ChatModelRequestContext requestContext, Span currentSpan) {
            currentSpan.setAttribute("--custom-on-request--", "--value-on-request--");
        }

        @Override
        public void onResponse(ChatModelResponseContext responseContext, Span currentSpan) {
            currentSpan.setAttribute("--custom-on-response--", "--value-on-response--");
        }

        @Override
        public void onError(ChatModelErrorContext errorContext, Span currentSpan) {
            currentSpan.setAttribute("--custom-on-error--", "--value-on-error--");
        }
    }

    @RegisterAiService(chatLanguageModelSupplier = EchoChatLanguageModelSupplier.class, chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
    interface AiService {
        @UserMessage("test")
        String test();
    }

    public static class EchoChatLanguageModelSupplier implements Supplier<ChatLanguageModel> {
        @Override
        public ChatLanguageModel get() {
            return (messages) -> new Response<>(new AiMessage(messages.get(0).text()));
        }
    }
}
