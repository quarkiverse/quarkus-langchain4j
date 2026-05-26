package io.quarkiverse.langchain4j.bedrock.runtime.client.async;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.http.async.AsyncExecuteRequest;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.async.SdkAsyncHttpResponseHandler;

public class VertxSdkAsyncHttpClient implements SdkAsyncHttpClient {

    private static final String CLIENT_NAME = "Quarkus LangChain4j";

    private final HttpClient vertxHttpClient;

    public VertxSdkAsyncHttpClient(HttpClient client) {
        vertxHttpClient = client;
    }

    @Override
    public CompletableFuture<Void> execute(AsyncExecuteRequest executeRequest) {
        CompletableFuture<Void> executeFuture = new CompletableFuture<>();
        SdkAsyncHttpResponseHandler responseHandler = executeRequest.responseHandler();
        SdkHttpRequest sdkRequest = executeRequest.request();

        RequestOptions requestOptions = new RequestOptions()
                .setAbsoluteURI(sdkRequest.getUri().toString())
                .setMethod(HttpMethod.valueOf(sdkRequest.method().name()));

        for (Map.Entry<String, List<String>> entry : sdkRequest.headers().entrySet()) {
            for (String value : entry.getValue()) {
                requestOptions.addHeader(entry.getKey(), value);
            }
        }

        executeRequest.requestContentPublisher().subscribe(new RequestBodyCollector(requestOptions, responseHandler,
                executeFuture));

        return executeFuture;
    }

    private void sendRequest(RequestOptions requestOptions, Buffer body,
            SdkAsyncHttpResponseHandler responseHandler, CompletableFuture<Void> executeFuture) {
        vertxHttpClient.request(requestOptions)
                .onComplete(new RequestResultHandler(body, responseHandler, executeFuture));
    }

    private static Map<String, List<String>> convertHeaders(HttpClientResponse response) {
        Map<String, List<String>> headers = new HashMap<>();
        for (String name : response.headers().names()) {
            headers.put(name, response.headers().getAll(name));
        }
        return headers;
    }

    @Override
    public void close() {
        vertxHttpClient.close();
    }

    @Override
    public String clientName() {
        return CLIENT_NAME;
    }

    private class RequestBodyCollector implements Subscriber<ByteBuffer> {
        private final Buffer body = Buffer.buffer();
        private final RequestOptions requestOptions;
        private final SdkAsyncHttpResponseHandler responseHandler;
        private final CompletableFuture<Void> executeFuture;

        RequestBodyCollector(RequestOptions requestOptions, SdkAsyncHttpResponseHandler responseHandler,
                CompletableFuture<Void> executeFuture) {
            this.requestOptions = requestOptions;
            this.responseHandler = responseHandler;
            this.executeFuture = executeFuture;
        }

        @Override
        public void onSubscribe(Subscription s) {
            s.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(ByteBuffer byteBuffer) {
            byte[] bytes = new byte[byteBuffer.remaining()];
            byteBuffer.get(bytes);
            body.appendBytes(bytes);
        }

        @Override
        public void onError(Throwable t) {
            responseHandler.onError(t);
            executeFuture.completeExceptionally(t);
        }

        @Override
        public void onComplete() {
            sendRequest(requestOptions, body, responseHandler, executeFuture);
        }
    }

    private static class RequestResultHandler implements Handler<AsyncResult<HttpClientRequest>> {
        private final Buffer body;
        private final SdkAsyncHttpResponseHandler responseHandler;
        private final CompletableFuture<Void> executeFuture;

        RequestResultHandler(Buffer body, SdkAsyncHttpResponseHandler responseHandler,
                CompletableFuture<Void> executeFuture) {
            this.body = body;
            this.responseHandler = responseHandler;
            this.executeFuture = executeFuture;
        }

        @Override
        public void handle(AsyncResult<HttpClientRequest> reqResult) {
            if (reqResult.failed()) {
                responseHandler.onError(reqResult.cause());
                executeFuture.completeExceptionally(reqResult.cause());
                return;
            }

            reqResult.result().send(body)
                    .onComplete(new ResponseResultHandler(responseHandler, executeFuture));
        }
    }

    private static class ResponseResultHandler implements Handler<AsyncResult<HttpClientResponse>> {
        private final SdkAsyncHttpResponseHandler responseHandler;
        private final CompletableFuture<Void> executeFuture;

        ResponseResultHandler(SdkAsyncHttpResponseHandler responseHandler, CompletableFuture<Void> executeFuture) {
            this.responseHandler = responseHandler;
            this.executeFuture = executeFuture;
        }

        @Override
        public void handle(AsyncResult<HttpClientResponse> respResult) {
            if (respResult.failed()) {
                responseHandler.onError(respResult.cause());
                executeFuture.completeExceptionally(respResult.cause());
                return;
            }

            HttpClientResponse response = respResult.result();

            responseHandler.onHeaders(SdkHttpResponse.builder()
                    .statusCode(response.statusCode())
                    .statusText(response.statusMessage())
                    .headers(convertHeaders(response))
                    .build());

            response.pause();
            responseHandler.onStream(new ResponseBodyPublisher(response, executeFuture));
        }
    }

    private static class ResponseBodyPublisher implements Publisher<ByteBuffer> {
        private final HttpClientResponse response;
        private final CompletableFuture<Void> executeFuture;

        ResponseBodyPublisher(HttpClientResponse response, CompletableFuture<Void> executeFuture) {
            this.response = response;
            this.executeFuture = executeFuture;
        }

        @Override
        public void subscribe(Subscriber<? super ByteBuffer> subscriber) {
            subscriber.onSubscribe(new ResponseBodySubscription(response));

            response.handler(new BodyChunkHandler(subscriber));
            response.endHandler(new StreamEndHandler(subscriber, executeFuture));
            response.exceptionHandler(new StreamErrorHandler(subscriber, executeFuture));
        }
    }

    private static class ResponseBodySubscription implements Subscription {
        private final HttpClientResponse response;

        ResponseBodySubscription(HttpClientResponse response) {
            this.response = response;
        }

        @Override
        public void request(long n) {
            response.resume();
        }

        @Override
        public void cancel() {
            response.pause();
        }
    }

    private static class BodyChunkHandler implements Handler<Buffer> {
        private final Subscriber<? super ByteBuffer> subscriber;

        BodyChunkHandler(Subscriber<? super ByteBuffer> subscriber) {
            this.subscriber = subscriber;
        }

        @Override
        public void handle(Buffer buffer) {
            subscriber.onNext(ByteBuffer.wrap(buffer.getBytes()));
        }
    }

    private static class StreamEndHandler implements Handler<Void> {
        private final Subscriber<? super ByteBuffer> subscriber;
        private final CompletableFuture<Void> executeFuture;

        StreamEndHandler(Subscriber<? super ByteBuffer> subscriber, CompletableFuture<Void> executeFuture) {
            this.subscriber = subscriber;
            this.executeFuture = executeFuture;
        }

        @Override
        public void handle(Void v) {
            subscriber.onComplete();
            executeFuture.complete(null);
        }
    }

    private static class StreamErrorHandler implements Handler<Throwable> {
        private final Subscriber<? super ByteBuffer> subscriber;
        private final CompletableFuture<Void> executeFuture;

        StreamErrorHandler(Subscriber<? super ByteBuffer> subscriber, CompletableFuture<Void> executeFuture) {
            this.subscriber = subscriber;
            this.executeFuture = executeFuture;
        }

        @Override
        public void handle(Throwable t) {
            subscriber.onError(t);
            executeFuture.completeExceptionally(t);
        }
    }

}
