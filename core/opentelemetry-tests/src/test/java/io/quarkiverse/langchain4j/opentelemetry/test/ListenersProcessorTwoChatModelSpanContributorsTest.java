package io.quarkiverse.langchain4j.opentelemetry.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.function.BiConsumer;

import jakarta.enterprise.context.ApplicationScoped;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.quarkiverse.langchain4j.runtime.listeners.ChatModelSpanContributor;
import io.quarkus.test.QuarkusUnitTest;

class ListenersProcessorTwoChatModelSpanContributorsTest
        extends ListenersProcessorAbstractSpanChatModelListenerTest {
    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> appWithInMemorySpanExporter()
                            .addClasses(
                                    FirstChatModelSpanContributor.class,
                                    SecondChatModelSpanContributor.class));

    static BiConsumer<ChatModelRequestContext, Span> onRequest;
    static BiConsumer<ChatModelResponseContext, Span> onResponse;
    static BiConsumer<ChatModelErrorContext, Span> onError;

    @BeforeEach
    void setupConsumers() {
        onRequest = mock(BiConsumer.class);
        onResponse = mock(BiConsumer.class);
        onError = mock(BiConsumer.class);
    }

    @Test
    void shouldHaveSpanChatModelListenerWitContributor() {
        assertThat(spanChatModelListener).isNotNull();
        assertThat(contributors)
                .hasSize(4);
    }

    @Test
    void shouldBeResilientToContributorFailuresOnRequest() {
        var ctx = MockedContexts.create();
        var failure = new RuntimeException("--failure-on-request-of-contributor--");
        doThrow(failure).when(onRequest).accept(any(), any());

        spanChatModelListener.onRequest(ctx.requestContext());
        spanChatModelListener.onResponse(ctx.responseContext());

        await().untilAsserted(() -> assertThat(exporter.getFinishedSpanItems()).hasSize(1));
        verify(onRequest, times(2)).accept(any(), any());
        var actualSpan = exporter.getFinishedSpanItems().get(0);
        assertThat(actualSpan.getEvents())
                .hasSize(2)
                .extracting("exception")
                .extracting("message")
                .contains("--failure-on-request-of-contributor--", "--failure-on-request-of-contributor--");
    }

    @Test
    void shouldBeResilientToContributorFailuresOnResponse() {
        var ctx = MockedContexts.create();
        var failure = new RuntimeException("--failure-on-response-of-contributor--");
        doThrow(failure).when(onResponse).accept(any(), any());

        spanChatModelListener.onRequest(ctx.requestContext());
        spanChatModelListener.onResponse(ctx.responseContext());

        await().untilAsserted(() -> assertThat(exporter.getFinishedSpanItems()).hasSize(1));
        verify(onResponse, times(2)).accept(any(), any());
        var actualSpan = exporter.getFinishedSpanItems().get(0);
        assertThat(actualSpan.getEvents())
                .hasSize(2)
                .extracting("exception")
                .extracting("message")
                .contains(
                        "--failure-on-response-of-contributor--", "--failure-on-response-of-contributor--");
    }

    @Test
    void shouldBeResilientToContributorFailuresOnError() {
        var ctx = MockedContexts.create();
        var failure = new RuntimeException("--failure-on-error-of-contributor--");
        doThrow(failure).when(onError).accept(any(), any());

        spanChatModelListener.onRequest(ctx.requestContext());
        spanChatModelListener.onError(ctx.errorContext());

        await().untilAsserted(() -> assertThat(exporter.getFinishedSpanItems()).hasSize(1));
        verify(onError, times(2)).accept(any(), any());
        var actualSpan = exporter.getFinishedSpanItems().get(0);
        assertThat(actualSpan.getEvents())
                .hasSize(3)
                .extracting("exception")
                .extracting("message")
                .contains(
                        "--failure-on-error-of-contributor--",
                        "--failure-on-error-of-contributor--",
                        ctx.errorContext().error().getMessage());
    }

    @Override
    protected void verifySuccessfulSpan(SpanData actualSpan) {
        verify(onRequest, times(2)).accept(any(), any());
        verify(onResponse, times(2)).accept(any(), any());
        verifyNoInteractions(onError);
    }

    @Override
    protected void verifyFailedSpan(SpanData actualSpan) {
        verify(onRequest, times(2)).accept(any(), any());
        verifyNoInteractions(onResponse);
        verify(onError, times(2)).accept(any(), any());
    }

    @ApplicationScoped
    public static class FirstChatModelSpanContributor extends AbstractChatModelSpanContributor {
    }

    @ApplicationScoped
    public static class SecondChatModelSpanContributor extends AbstractChatModelSpanContributor {
    }

    public static class AbstractChatModelSpanContributor implements ChatModelSpanContributor {
        @Override
        public void onRequest(ChatModelRequestContext requestContext, Span currentSpan) {
            onRequest.accept(requestContext, currentSpan);
        }

        @Override
        public void onResponse(ChatModelResponseContext responseContext, Span currentSpan) {
            onResponse.accept(responseContext, currentSpan);
        }

        @Override
        public void onError(ChatModelErrorContext errorContext, Span currentSpan) {
            onError.accept(errorContext, currentSpan);
        }
    }
}
