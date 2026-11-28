package io.quarkiverse.langchain4j.runtime.aiservice;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.opentelemetry.context.Scope;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.operators.AbstractMulti;
import io.smallrye.mutiny.subscription.MultiSubscriber;

/**
 * Keeps the AI service span open until the returned stream terminates.
 * <p>
 * The model call of a streaming AI service method starts when the stream is subscribed to, so the span is made current
 * while the subscription runs, which parents the model and tool spans to it. The emitted items are handed to the span,
 * which collects the result when configured to. The span ends on completion, failure or cancellation, whichever comes
 * first.
 * <p>
 * Every subscription calls the model again, so every subscription has its own span, and with it its own result. The
 * first subscription takes the span started when the method was called, the later ones, such as a retry after a
 * failure, get a new span from {@code nextSpan}.
 */
final class SpanEndingMulti<T> extends AbstractMulti<T> {

    private final Multi<T> upstream;
    private final AiServiceSpan firstSpan;
    private final Supplier<AiServiceSpan> nextSpan;
    private final AtomicBoolean isFirstSpanTaken = new AtomicBoolean(false);

    SpanEndingMulti(Multi<T> upstream, AiServiceSpan firstSpan, Supplier<AiServiceSpan> nextSpan) {
        this.upstream = upstream;
        this.firstSpan = firstSpan;
        this.nextSpan = nextSpan;
    }

    @Override
    public void subscribe(MultiSubscriber<? super T> subscriber) {
        AiServiceSpan span = isFirstSpanTaken.compareAndSet(false, true) ? firstSpan : nextSpan.get();
        Multi<T> traced = upstream
                .onItem().invoke(new Consumer<T>() {
                    @Override
                    public void accept(T item) {
                        span.onStreamItem(item);
                    }
                })
                .onTermination().invoke(new BiConsumer<Throwable, Boolean>() {
                    @Override
                    public void accept(Throwable failure, Boolean cancelled) {
                        if (failure != null) {
                            span.fail(failure);
                        } else if (cancelled) {
                            span.cancel();
                        } else {
                            span.completeStream();
                        }
                    }
                });
        try (Scope ignored = span.makeCurrent()) {
            traced.subscribe(subscriber);
        }
    }
}
