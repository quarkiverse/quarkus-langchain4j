package io.quarkiverse.langchain4j.bedrock.deployment;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;
import software.amazon.eventstream.HeaderValue;
import software.amazon.eventstream.Message;

public class BedrockStreamHelper implements StreamingChatResponseHandler {

    private final AtomicReference<ChatResponse> responseRef = new AtomicReference<>();
    private final AtomicReference<Throwable> errorRef = new AtomicReference<>();
    private final CountDownLatch latch = new CountDownLatch(1);

    public static BedrockStreamHelper create() {
        return new BedrockStreamHelper();
    }

    @Override
    public void onPartialResponse(final String partialResponse) {
    }

    @Override
    public void onCompleteResponse(final ChatResponse completeResponse) {
        responseRef.set(completeResponse);
        latch.countDown();
    }

    @Override
    public void onError(final Throwable error) {
        errorRef.set(error);
    }

    public ChatResponse awaitResponse() throws Throwable {
        latch.await(10, TimeUnit.SECONDS);

        if (errorRef.get() != null) {
            throw errorRef.get();
        }

        return responseRef.get();
    }

    public static byte[] decode(final List<Message> messages) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        messages.forEach(x -> x.encode(baos));
        ByteBuffer buf = ByteBuffer.wrap(baos.toByteArray());

        if (buf.remaining() > 0) {
            int bufSize = Math.min(1024, buf.remaining());
            byte[] bs = new byte[bufSize];
            buf.get(bs);
            return bs;
        }
        throw new IllegalArgumentException("no bytes defined");
    }

    // Converse
    public static Message createMessageStart(final ConversationRole role) {
        return createMessageStart(role.toString());
    }

    public static Message createMessageStart(final String role) {
        return new Message(createHeaders("messageStart"), """
                {
                  "role": "%s"
                }
                """.formatted(role).getBytes(StandardCharsets.UTF_8));
    }

    public static Message createContentBlockDelta(final int index, final String text) {
        return new Message(createHeaders("contentBlockDelta"), """
                {
                  "contentBlockIndex": %d,
                  "delta": {
                    "text": "%s"
                  }
                }
                """.formatted(index, text).getBytes(StandardCharsets.UTF_8));
    }

    public static Message createContentBlockStop(final int index) {
        return new Message(createHeaders("contentBlockStop"), """
                {
                  "contentBlockIndex": %d
                }
                """.formatted(index).getBytes(StandardCharsets.UTF_8));
    }

    public static Message createMessageStop(final StopReason reason) {
        return createMessageStop(reason.toString());
    }

    public static Message createMessageStop(final String reason) {
        return new Message(createHeaders("messageStop"), """
                {
                  "stopReason": "%s"
                }
                """.formatted(reason).getBytes(StandardCharsets.UTF_8));
    }

    // InvokeModelWithResponseStream
    public static Message createCompletion(final String completion) {
        var byteArray = """
                {
                  "completion": "%s"
                }
                """.formatted(completion).getBytes(StandardCharsets.UTF_8);
        // Encode the byte array to base64
        String base64Encoded = Base64.getEncoder().encodeToString(byteArray);

        var msg = """
                {
                  "bytes": "%s"
                }
                """.formatted(base64Encoded);

        System.out.println(msg);

        return new Message(createHeaders("chunk"), msg.getBytes(StandardCharsets.UTF_8));
    }

    public static Message createMetadata(final int latencyMs, final int inputTokens, final int outputTokens) {
        return new Message(createHeaders("metadata"), """
                {
                  "metrics": {
                    "latencyMs": %d
                  },
                    "usage": {
                        "inputTokens": %d,
                        "outputTokens": %d,
                        "totalTokens": %d
                    }
                }
                """.formatted(latencyMs, inputTokens, outputTokens, inputTokens + outputTokens)
                .getBytes(StandardCharsets.UTF_8));
    }

    private static Map<String, HeaderValue> createHeaders(final String eventType) {
        return Map.of(
                ":message-type", HeaderValue.fromString("event"),
                ":event-type", HeaderValue.fromString(eventType),
                ":content-type", HeaderValue.fromString("application/json"));
    }
}
