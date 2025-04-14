package io.quarkiverse.langchain4j.bedrock.runtime.jaxrsclient;

import static io.quarkiverse.langchain4j.bedrock.runtime.jaxrsclient.JaxRsSdkHttpClientHelper.getResponseHeaders;

import java.io.InputStream;
import java.util.concurrent.atomic.AtomicMarkableReference;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.core.Response;

import org.jboss.resteasy.reactive.ClientWebApplicationException;

import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.http.ContentStreamProvider;
import software.amazon.awssdk.http.ExecutableHttpRequest;
import software.amazon.awssdk.http.HttpExecuteRequest;
import software.amazon.awssdk.http.HttpExecuteResponse;
import software.amazon.awssdk.http.SdkHttpFullResponse;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.SdkHttpResponse;

public class JaxRsSdkHttpClientExecutable implements ExecutableHttpRequest {
    private final Invocation.Builder invocationBuilder;
    private final HttpExecuteRequest executeRequest;
    private final AtomicMarkableReference<Response> cancellableRef = new AtomicMarkableReference<>(null, false);

    public JaxRsSdkHttpClientExecutable(final Invocation.Builder invocationBuilder,
            final HttpExecuteRequest executeRequest) {
        this.invocationBuilder = invocationBuilder;
        this.executeRequest = executeRequest;
    }

    @Override
    public HttpExecuteResponse call() {
        if (cancellableRef.isMarked()) {
            throw new IllegalStateException("Request has been aborted");
        }

        Response response = execute();
        if (!cancellableRef.compareAndSet(null, response, false, false)) {
            response.close();
            throw new IllegalStateException("Request has been aborted");
        }

        if (response.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
            throw new ClientWebApplicationException(response.getStatus());
        }

        return createSdkResponse(response);
    }

    private HttpExecuteResponse createSdkResponse(final Response response) {
        final SdkHttpFullResponse.Builder builder = SdkHttpResponse.builder()
                .statusCode(response.getStatus())
                .statusText(response.getStatusInfo().getReasonPhrase())
                .headers(getResponseHeaders(response));

        final InputStream inputStream = response.readEntity(InputStream.class);
        final AbortableInputStream abortableInputStream = inputStream != null
                ? AbortableInputStream.create(inputStream, response::close)
                : null;

        return HttpExecuteResponse.builder()
                .response(builder.build())
                .responseBody(abortableInputStream)
                .build();
    }

    @Override
    public void abort() {
        // The JaxRs client cannot cancel the request. So we wait until the response is created and cancel it
        // immediately. If the response is not yet created we mark it for close, so that the call will cancel it.
        while (!cancellableRef.isMarked()) {
            final Response actualResponse = cancellableRef.getReference();
            if (cancellableRef.compareAndSet(actualResponse, actualResponse, false, true) && actualResponse != null) {
                actualResponse.close();
            }
        }
    }

    private Response execute() {
        final SdkHttpRequest request = executeRequest.httpRequest();
        final InputStream inputStream = executeRequest.contentStreamProvider().map(ContentStreamProvider::newStream)
                .orElse(null);

        final String contentType = request.headers().get("content-type").get(0);

        return switch (request.method()) {
            case GET -> invocationBuilder.get();
            case POST -> invocationBuilder.post(Entity.entity(inputStream, contentType));
            case PUT -> invocationBuilder.put(Entity.entity(inputStream, contentType));
            case DELETE -> invocationBuilder.delete();
            case HEAD -> invocationBuilder.head();
            case PATCH -> invocationBuilder.method("PATCH", Entity.entity(inputStream, contentType));
            case OPTIONS -> invocationBuilder.options();
        };
    }
}
