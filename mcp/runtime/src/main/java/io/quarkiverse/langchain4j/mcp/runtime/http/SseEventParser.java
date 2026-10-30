package io.quarkiverse.langchain4j.mcp.runtime.http;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.parsetools.RecordParser;

/**
 * Chunk-safe Server-Sent Events parser.
 * <p>
 * Framing is delegated to a Vert.x {@link RecordParser} delimited by {@code \n}: it accumulates the
 * incoming byte chunks and re-emits them one line at a time, transparently reassembling lines that
 * straddle a network chunk boundary. Because {@code \n} (0x0A) never occurs inside a multi-byte
 * UTF-8 sequence, every emitted line is a whole number of UTF-8 characters and can be decoded
 * safely; a trailing {@code \r} is stripped so both {@code \n} and {@code \r\n} endings work.
 * <p>
 * Field parsing follows the SSE specification: each line is {@code field:value}, a single space
 * after the colon is optional, a line starting with a colon is a comment, a line without a colon has
 * an empty value, and multiple {@code data} lines are joined with a newline. A blank line ends the
 * current event, at which point the resulting {@link Event} is handed to the consumer.
 */
class SseEventParser implements Handler<Buffer> {

    record Event(String id, String name, String data, String comment, Long retry) {
    }

    private final Consumer<Event> consumer;
    private final RecordParser lineParser = RecordParser.newDelimited("\n", this::onLine);

    private boolean started;
    private String id;
    private String name;
    private Long retry;
    private StringBuilder data;
    private StringBuilder comment;

    SseEventParser(Consumer<Event> consumer) {
        this.consumer = consumer;
    }

    @Override
    public void handle(Buffer chunk) {
        lineParser.handle(chunk);
    }

    private void onLine(Buffer buffer) {
        String line = buffer.toString(StandardCharsets.UTF_8);
        // RecordParser strips the \n delimiter, so a \r\n ending leaves a trailing \r to drop
        if (line.endsWith("\r")) {
            line = line.substring(0, line.length() - 1);
        }
        if (line.isEmpty()) {
            dispatch();
            return;
        }
        started = true;
        int colon = line.indexOf(':');
        String field = colon < 0 ? line : line.substring(0, colon);
        String value = colon < 0 ? "" : line.substring(colon + 1);
        // both 'data:{}' and 'data: {}' are valid, only a single leading space is stripped
        if (value.startsWith(" ")) {
            value = value.substring(1);
        }
        switch (field) {
            case "" -> comment = append(comment, value);
            case "data" -> data = append(data, value);
            case "event" -> name = value;
            case "id" -> id = value;
            case "retry" -> {
                try {
                    retry = Long.parseLong(value.trim());
                } catch (NumberFormatException e) {
                    // spec says to ignore an unparseable retry value
                }
            }
            default -> {
                // ignore unknown fields per the SSE specification
            }
        }
    }

    private void dispatch() {
        if (!started) {
            // ignore blank lines that are not preceded by any field (e.g. keep-alive newlines)
            return;
        }
        consumer.accept(new Event(id, name, data == null ? null : data.toString(),
                comment == null ? null : comment.toString(), retry));
        started = false;
        id = null;
        name = null;
        retry = null;
        data = null;
        comment = null;
    }

    private static StringBuilder append(StringBuilder buffer, String value) {
        if (buffer == null) {
            return new StringBuilder(value);
        }
        return buffer.append('\n').append(value);
    }
}
