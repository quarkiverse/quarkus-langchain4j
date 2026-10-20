package io.quarkiverse.langchain4j.mockapi.internal.dialect;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;

import io.quarkiverse.langchain4j.mockapi.SseEvent;

public record Rendered(int status, Map<String, String> headers, byte[] body, List<SseEvent> events,
        StreamFormat streamFormat) {

    /**
     * How a stream's events are written: each format has its {@code Content-Type} and frames every event on its own.
     */
    public enum StreamFormat {
        NONE(null),
        /**
         * Server-sent events: an {@code event:} line when the event has a name, the {@code data:} line and a blank line.
         */
        SSE("text/event-stream"),
        /**
         * Newline-delimited JSON: each event's data on a line of its own.
         */
        NDJSON("application/x-ndjson"),
        /**
         * The AWS event stream encoding: each event's name is its {@code :event-type} and its data the JSON payload.
         */
        AWS_EVENT_STREAM("application/vnd.amazon.eventstream");

        private final String contentType;

        StreamFormat(String contentType) {
            this.contentType = contentType;
        }

        /**
         * The {@code Content-Type} of a stream in this format, null for {@link #NONE}.
         */
        public String contentType() {
            return contentType;
        }

        /**
         * One event as written to the stream.
         */
        public byte[] frame(SseEvent e) {
            return switch (this) {
                case NONE -> throw new IllegalStateException("not a stream");
                case SSE -> ((e.event() != null ? "event: " + e.event() + "\n" : "") + "data: " + e.data() + "\n\n")
                        .getBytes(StandardCharsets.UTF_8);
                case NDJSON -> (e.data() + "\n").getBytes(StandardCharsets.UTF_8);
                case AWS_EVENT_STREAM -> awsEventStreamMessage(e.event(), e.data());
            };
        }
    }

    public static Rendered json(int status, byte[] body) {
        return new Rendered(status, Map.of("Content-Type", "application/json"), body, List.of(), StreamFormat.NONE);
    }

    /**
     * A 200 stream of the events in the given format, which sets the {@code Content-Type}.
     */
    public static Rendered stream(StreamFormat format, List<SseEvent> events) {
        return new Rendered(200, Map.of(), null, events, format);
    }

    /**
     * One message of the AWS event stream encoding
     * (<a href=
     * "https://docs.aws.amazon.com/transcribe/latest/dg/streaming-setting-up.html#streaming-event-stream">https://docs.aws.amazon.com/transcribe/latest/dg/streaming-setting-up.html#streaming-event-stream</a>):
     * the prelude
     * (total length and headers length, 4-byte big-endian each), the prelude CRC, the {@code :event-type},
     * {@code :content-type} and {@code :message-type} headers as STRING (7) values, the payload, and the CRC32 of
     * everything before it.
     */
    private static byte[] awsEventStreamMessage(String eventType, String payload) {
        ByteArrayOutputStream headers = new ByteArrayOutputStream();
        stringHeader(headers, ":event-type", eventType);
        stringHeader(headers, ":content-type", "application/json");
        stringHeader(headers, ":message-type", "event");
        byte[] headerBytes = headers.toByteArray();
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
        int totalLength = 16 + headerBytes.length + payloadBytes.length;

        ByteBuffer message = ByteBuffer.allocate(totalLength);
        message.putInt(totalLength);
        message.putInt(headerBytes.length);
        message.putInt((int) crc32(message.array(), 8));
        message.put(headerBytes);
        message.put(payloadBytes);
        message.putInt((int) crc32(message.array(), totalLength - 4));
        return message.array();
    }

    private static void stringHeader(ByteArrayOutputStream out, String name, String value) {
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        byte[] valueBytes = value.getBytes(StandardCharsets.UTF_8);
        out.write(nameBytes.length);
        out.writeBytes(nameBytes);
        out.write(7);
        out.write(valueBytes.length >> 8);
        out.write(valueBytes.length);
        out.writeBytes(valueBytes);
    }

    private static long crc32(byte[] bytes, int length) {
        CRC32 crc = new CRC32();
        crc.update(bytes, 0, length);
        return crc.getValue();
    }
}
