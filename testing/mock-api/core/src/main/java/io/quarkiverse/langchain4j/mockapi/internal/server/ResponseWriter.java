package io.quarkiverse.langchain4j.mockapi.internal.server;

import java.util.Map;

import io.quarkiverse.langchain4j.mockapi.SseEvent;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Rendered;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerResponse;

final class ResponseWriter {

    private ResponseWriter() {
    }

    static void write(HttpServerResponse resp, Rendered r) {
        resp.setStatusCode(r.status());
        for (Map.Entry<String, String> h : r.headers().entrySet()) {
            resp.putHeader(h.getKey(), h.getValue());
        }
        if (r.streamFormat() == Rendered.StreamFormat.NONE) {
            if (r.body() == null) {
                resp.end();
            } else {
                resp.end(Buffer.buffer(r.body()));
            }
            return;
        }
        resp.putHeader("Content-Type", r.streamFormat().contentType());
        resp.setChunked(true);
        for (SseEvent e : r.events()) {
            resp.write(Buffer.buffer(r.streamFormat().frame(e)));
        }
        resp.end();
    }
}
