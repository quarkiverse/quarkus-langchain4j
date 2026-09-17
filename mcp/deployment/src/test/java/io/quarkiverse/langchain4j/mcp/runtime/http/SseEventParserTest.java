package io.quarkiverse.langchain4j.mcp.runtime.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.vertx.core.buffer.Buffer;

class SseEventParserTest {

    private final List<SseEventParser.Event> events = new ArrayList<>();
    private final SseEventParser parser = new SseEventParser(events::add);

    private void feed(String s) {
        parser.handle(Buffer.buffer(s.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void parsesDataWithoutSpaceAfterColon() {
        feed("id:1\nevent:message\ndata:{\"jsonrpc\":\"2.0\"}\n\n");

        assertThat(events).hasSize(1);
        SseEventParser.Event event = events.get(0);
        assertThat(event.id()).isEqualTo("1");
        assertThat(event.name()).isEqualTo("message");
        assertThat(event.data()).isEqualTo("{\"jsonrpc\":\"2.0\"}");
    }

    @Test
    void stripsOnlyASingleLeadingSpaceAfterColon() {
        feed("data: {\"a\":1}\n\n");
        feed("data:  indented\n\n");

        assertThat(events).hasSize(2);
        assertThat(events.get(0).data()).isEqualTo("{\"a\":1}");
        assertThat(events.get(1).data()).isEqualTo(" indented");
    }

    @Test
    void mergesMultipleDataLinesWithNewline() {
        feed("data:line1\ndata:line2\n\n");

        assertThat(events).hasSize(1);
        assertThat(events.get(0).data()).isEqualTo("line1\nline2");
    }

    @Test
    void parsesCommentLines() {
        feed(":this is a comment\n\n");

        assertThat(events).hasSize(1);
        assertThat(events.get(0).comment()).isEqualTo("this is a comment");
        assertThat(events.get(0).data()).isNull();
    }

    @Test
    void parsesRetryAsNumber() {
        feed("retry:1234\n\n");

        assertThat(events).hasSize(1);
        assertThat(events.get(0).retry()).isEqualTo(1234L);
    }

    @Test
    void parsesMultipleEventsInOneChunk() {
        feed("event:message\ndata:a\n\nevent:message\ndata:b\n\n");

        assertThat(events).hasSize(2);
        assertThat(events.get(0).data()).isEqualTo("a");
        assertThat(events.get(1).data()).isEqualTo("b");
    }

    @Test
    void handlesCrlfLineAndEventSeparators() {
        feed("event:message\r\ndata:{\"x\":1}\r\n\r\n");

        assertThat(events).hasSize(1);
        assertThat(events.get(0).name()).isEqualTo("message");
        assertThat(events.get(0).data()).isEqualTo("{\"x\":1}");
    }

    @Test
    void deliversEventSplitAcrossChunks() {
        // the event separator only arrives in the second chunk
        feed("event:message\ndata:{\"x\"");
        assertThat(events).isEmpty();

        feed(":1}\n\n");

        assertThat(events).hasSize(1);
        assertThat(events.get(0).data()).isEqualTo("{\"x\":1}");
    }

    @Test
    void keepsMultibyteUtf8CharSplitAcrossChunks() {
        // "data:€\n\n" where the 3-byte € (E2 82 AC) is split between two network chunks
        byte[] all = "data:€\n\n".getBytes(StandardCharsets.UTF_8);
        int splitInsideEuro = 6; // after "data:" (5 bytes) + first byte of €
        parser.handle(Buffer.buffer(Arrays.copyOfRange(all, 0, splitInsideEuro)));
        parser.handle(Buffer.buffer(Arrays.copyOfRange(all, splitInsideEuro, all.length)));

        assertThat(events).hasSize(1);
        assertThat(events.get(0).data()).isEqualTo("€");
    }
}
