package io.quarkiverse.langchain4j.testing.a2aserver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import org.a2aproject.sdk.grpc.A2AServiceGrpc;
import org.a2aproject.sdk.grpc.GetTaskRequest;
import org.a2aproject.sdk.grpc.Message;
import org.a2aproject.sdk.grpc.Part;
import org.a2aproject.sdk.grpc.Role;
import org.a2aproject.sdk.grpc.SendMessageRequest;
import org.a2aproject.sdk.grpc.SendMessageResponse;
import org.a2aproject.sdk.grpc.Task;
import org.a2aproject.sdk.grpc.TaskState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

/**
 * Exercises the gRPC transport, which an application opts into by putting the gRPC reference implementation on the
 * classpath. Unlike the two HTTP bindings it speaks protobuf over HTTP/2, so the messages are built from the stubs
 * the SDK generates rather than written out as JSON.
 */
@QuarkusTest
public class GrpcTransportTest {

    private static final Metadata.Key<String> VERSION_KEY = Metadata.Key.of("A2A-Version",
            Metadata.ASCII_STRING_MARSHALLER);

    private ManagedChannel channel;

    /**
     * The port comes from REST Assured because it is the one place that knows it in both modes: a native
     * integration test runs against a launched process, where nothing can be injected.
     */
    @BeforeEach
    void openChannel() {
        channel = ManagedChannelBuilder.forAddress("localhost", RestAssured.port).usePlaintext().build();
    }

    @AfterEach
    void closeChannel() {
        channel.shutdownNow();
    }

    @Test
    public void testSendMessageIsAnsweredByTheAgent() {
        SendMessageResponse response = blockingStub().sendMessage(sendMessageRequest("message-1", "weather in LA?"));

        assertThat(response.hasTask()).isTrue();
        assertThat(response.getTask().getStatus().getState()).isEqualTo(TaskState.TASK_STATE_COMPLETED);
        assertThat(response.getTask().getArtifacts(0).getParts(0).getText()).isEqualTo("You asked: weather in LA?");
    }

    @Test
    public void testTaskCanBeFetchedBackAfterTheMessageWasAnswered() {
        String taskId = blockingStub().sendMessage(sendMessageRequest("message-2", "weather in NY?"))
                .getTask().getId();

        Task task = blockingStub().getTask(GetTaskRequest.newBuilder().setId(taskId).build());

        assertThat(task.getId()).isEqualTo(taskId);
        assertThat(task.getArtifacts(0).getParts(0).getText()).isEqualTo("You asked: weather in NY?");
    }

    /**
     * An error travels as a {@code google.rpc.Status} carrying an {@code ErrorInfo}, which the binding builds
     * reflectively, so this is the call that shows those messages are reachable in a native image.
     */
    @Test
    public void testUnknownTaskIsReportedAsNotFound() {
        StatusRuntimeException error = catchThrowableOfType(StatusRuntimeException.class,
                () -> blockingStub().getTask(GetTaskRequest.newBuilder().setId("no-such-task").build()));

        assertThat(error.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND);
    }

    /**
     * The version the client speaks travels in call metadata here, where the HTTP bindings put it in a header. This
     * transport checks it on every method, unlike JSON-RPC which only checks it when a message is sent.
     */
    private A2AServiceGrpc.A2AServiceBlockingStub blockingStub() {
        Metadata metadata = new Metadata();
        metadata.put(VERSION_KEY, "1.0");
        return A2AServiceGrpc.newBlockingStub(channel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));
    }

    private static SendMessageRequest sendMessageRequest(String messageId, String text) {
        return SendMessageRequest.newBuilder()
                .setMessage(Message.newBuilder()
                        .setMessageId(messageId)
                        .setContextId("context-" + messageId)
                        .setRole(Role.ROLE_USER)
                        .addParts(Part.newBuilder().setText(text)))
                .build();
    }
}
