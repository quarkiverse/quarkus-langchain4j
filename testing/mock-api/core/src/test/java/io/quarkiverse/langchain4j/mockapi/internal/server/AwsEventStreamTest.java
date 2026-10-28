package io.quarkiverse.langchain4j.mockapi.internal.server;

import static io.quarkiverse.langchain4j.mockapi.internal.dialect.Rendered.StreamFormat.AWS_EVENT_STREAM;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.CRC32;

import org.junit.jupiter.api.Test;

import io.quarkiverse.langchain4j.mockapi.SseEvent;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Rendered;

/**
 * Decodes a message written by {@link Rendered.StreamFormat#frame} following the event stream encoding
 * (<a href=
 * "https://docs.aws.amazon.com/transcribe/latest/dg/streaming-setting-up.html#streaming-event-stream">https://docs.aws.amazon.com/transcribe/latest/dg/streaming-setting-up.html#streaming-event-stream</a>).
 */
class AwsEventStreamTest {

    @Test
    void encodesOneEventAsAPreludeTypedStringHeadersPayloadAndBothCrcs() {
        String payload = "{\"contentBlockIndex\":0,\"delta\":{\"text\":\"Hé\"}}";
        byte[] message = AWS_EVENT_STREAM.frame(SseEvent.of("contentBlockDelta", payload));
        ByteBuffer buf = ByteBuffer.wrap(message);

        // prelude: total length and headers length, both 4-byte big-endian, then the prelude CRC
        int totalLength = buf.getInt();
        int headersLength = buf.getInt();
        assertThat(totalLength).isEqualTo(message.length);
        assertThat(Integer.toUnsignedLong(buf.getInt())).isEqualTo(crc(message, 0, 8));

        Map<String, String> headers = new LinkedHashMap<>();
        int headersEnd = 12 + headersLength;
        while (buf.position() < headersEnd) {
            byte[] name = new byte[buf.get() & 0xff];
            buf.get(name);
            assertThat(buf.get()).as("value type STRING").isEqualTo((byte) 7);
            byte[] value = new byte[buf.getShort() & 0xffff];
            buf.get(value);
            headers.put(new String(name, StandardCharsets.UTF_8), new String(value, StandardCharsets.UTF_8));
        }
        assertThat(buf.position()).isEqualTo(headersEnd);
        assertThat(headers).containsExactly(
                Map.entry(":event-type", "contentBlockDelta"),
                Map.entry(":content-type", "application/json"),
                Map.entry(":message-type", "event"));

        // payload, then the message CRC over everything before it; 16 bytes of overhead in total
        byte[] body = new byte[totalLength - headersEnd - 4];
        buf.get(body);
        assertThat(new String(body, StandardCharsets.UTF_8)).isEqualTo(payload);
        assertThat(Integer.toUnsignedLong(buf.getInt())).isEqualTo(crc(message, 0, totalLength - 4));
        assertThat(buf.remaining()).isZero();
        assertThat(totalLength - headersLength - body.length).isEqualTo(16);
    }

    @Test
    void emptyPayloadIsAValidMessage() {
        byte[] message = AWS_EVENT_STREAM.frame(SseEvent.of("messageStart", ""));
        ByteBuffer buf = ByteBuffer.wrap(message);
        int totalLength = buf.getInt();
        int headersLength = buf.getInt();

        assertThat(totalLength).isEqualTo(16 + headersLength);
        assertThat(Integer.toUnsignedLong(ByteBuffer.wrap(message, totalLength - 4, 4).getInt()))
                .isEqualTo(crc(message, 0, totalLength - 4));
    }

    private static long crc(byte[] bytes, int offset, int length) {
        CRC32 crc = new CRC32();
        crc.update(bytes, offset, length);
        return crc.getValue();
    }
}
