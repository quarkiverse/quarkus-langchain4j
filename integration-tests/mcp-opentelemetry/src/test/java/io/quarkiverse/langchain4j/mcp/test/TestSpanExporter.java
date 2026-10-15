package io.quarkiverse.langchain4j.mcp.test;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.enterprise.context.ApplicationScoped;

import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;

/**
 * Collects exported spans in memory. Unlike {@link io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter},
 * it also keeps the spans exported before the last {@link #reset()}, so that tests can verify the spans
 * created when the MCP client was initialized.
 */
@ApplicationScoped
public class TestSpanExporter implements SpanExporter {

    private final List<SpanData> allSpans = new CopyOnWriteArrayList<>();
    private final List<SpanData> spans = new CopyOnWriteArrayList<>();

    @Override
    public CompletableResultCode export(Collection<SpanData> exported) {
        allSpans.addAll(exported);
        spans.addAll(exported);
        return CompletableResultCode.ofSuccess();
    }

    /**
     * The spans exported since the last {@link #reset()}.
     */
    public List<SpanData> getFinishedSpanItems() {
        return spans;
    }

    /**
     * All spans exported since the application started.
     */
    public List<SpanData> getAllSpans() {
        return allSpans;
    }

    public void reset() {
        spans.clear();
    }

    @Override
    public CompletableResultCode flush() {
        return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode shutdown() {
        return CompletableResultCode.ofSuccess();
    }
}
