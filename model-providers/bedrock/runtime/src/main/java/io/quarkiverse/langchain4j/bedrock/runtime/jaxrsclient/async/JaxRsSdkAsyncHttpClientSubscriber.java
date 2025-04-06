package io.quarkiverse.langchain4j.bedrock.runtime.jaxrsclient.async;

import static io.quarkiverse.langchain4j.bedrock.runtime.jaxrsclient.JaxRsSdkHttpClientHelper.getResponseHeaders;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.core.Response;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.http.async.AsyncExecuteRequest;

public class JaxRsSdkAsyncHttpClientSubscriber implements Subscriber<ByteBuffer> {

    private final AsyncExecuteRequest executeRequest;
    private final CompletableFuture<Void> executeFuture;
    private final Invocation.Builder invocationBuilder;

    public JaxRsSdkAsyncHttpClientSubscriber(
            final AsyncExecuteRequest executeRequest,
            final Invocation.Builder invocationBuilder, final CompletableFuture<Void> executeFuture) {
        this.executeRequest = executeRequest;
        this.invocationBuilder = invocationBuilder;
        this.executeFuture = executeFuture;
    }

    @Override
    public void onSubscribe(final Subscription subscription) {
        subscription.request(1);
    }

    @Override
    public void onNext(final ByteBuffer byteBuffer) {
        Future<Response> response = execute(byteBuffer);

        if (!(response instanceof CompletableFuture<Response>)) {
            String msg = "Response is not a CompletableFuture. We cannot handle a simple Future.";
            IllegalArgumentException failure = new IllegalArgumentException(msg);
            executeRequest.responseHandler().onError(failure);
            executeFuture.completeExceptionally(failure);
            return;
        }

        ((CompletableFuture<Response>) response).whenComplete(new ResponseHandler(executeRequest, executeFuture));
    }

    private Future<Response> execute(final ByteBuffer byteBuffer) {
        final SdkHttpRequest request = executeRequest.request();

        final InputStream inputStream = new ByteArrayInputStream(byteBuffer.array());

        final String contentType = request.headers().get("content-type").get(0);

        return switch (request.method()) {
            case GET -> invocationBuilder.async().get();
            case POST -> invocationBuilder.async().post(Entity.entity(inputStream, contentType));
            case PUT -> invocationBuilder.async().put(Entity.entity(inputStream, contentType));
            case DELETE -> invocationBuilder.async().delete();
            case HEAD -> invocationBuilder.async().head();
            case PATCH -> invocationBuilder.async().method("PATCH", Entity.entity(inputStream, contentType));
            case OPTIONS -> invocationBuilder.async().options();
        };
    }

    @Override
    public void onError(final Throwable throwable) {
        executeRequest.responseHandler().onError(throwable);
        executeFuture.completeExceptionally(throwable);
    }

    @Override
    public void onComplete() {
        // this is handled onNext when the response is available.
    }

    private static class ResponseHandler implements BiConsumer<Response, Throwable> {

        private final AsyncExecuteRequest executeRequest;
        private final CompletableFuture<Void> executeFuture;

        private ResponseHandler(final AsyncExecuteRequest executeRequest, final CompletableFuture<Void> executeFuture) {
            this.executeRequest = executeRequest;
            this.executeFuture = executeFuture;
        }

        @Override
        public void accept(final Response response, final Throwable throwable) {
            if (throwable != null) {
                executeRequest.responseHandler().onError(throwable);
                executeFuture.completeExceptionally(throwable);
                return;
            }

            ByteBuffer content = ByteBuffer.wrap(response.readEntity(byte[].class));

            executeRequest.responseHandler().onHeaders(SdkHttpResponse.builder()
                    .headers(getResponseHeaders(response))
                    .statusCode(response.getStatus())
                    .statusText(response.getStatusInfo().getReasonPhrase())
                    .build());

            executeRequest.responseHandler().onStream(new ResponsePublisher(executeFuture, content));
        }
    }

    private static class ResponsePublisher implements Publisher<ByteBuffer> {

        private final CompletableFuture<Void> executeFuture;
        private final ByteBuffer content;
        private boolean cancelled = false;

        private ResponsePublisher(final CompletableFuture<Void> executeFuture, final ByteBuffer content) {
            this.executeFuture = executeFuture;
            this.content = content;
        }

        @Override
        public void subscribe(final Subscriber<? super ByteBuffer> subscriber) {
            subscriber.onSubscribe(new Subscription() {
                @Override
                public void request(final long l) {
                    if (cancelled) {
                        return;
                    }

                    if (l <= 0) {
                        subscriber.onError(new IllegalArgumentException("Demand must be positive"));
                    } else {
                        if (content.hasRemaining()) {
                            subscriber.onNext(content);
                        }
                        subscriber.onComplete();
                        executeFuture.complete(null);
                    }
                }

                @Override
                public void cancel() {
                    cancelled = true;
                }
            });
        }
    }
}
