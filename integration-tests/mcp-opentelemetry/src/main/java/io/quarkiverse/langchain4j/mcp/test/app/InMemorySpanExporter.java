package io.quarkiverse.langchain4j.mcp.test.app;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.enterprise.context.ApplicationScoped;

import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.quarkus.arc.profile.IfBuildProfile;

/**
 * Keeps the exported spans in memory, so that {@link TracingResource} can return them to the native tests.
 */
@IfBuildProfile("prod")
@ApplicationScoped
public class InMemorySpanExporter implements SpanExporter {

    private final List<SpanData> spans = new CopyOnWriteArrayList<>();

    @Override
    public CompletableResultCode export(Collection<SpanData> exported) {
        spans.addAll(exported);
        return CompletableResultCode.ofSuccess();
    }

    public List<SpanData> getSpans() {
        return spans;
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
