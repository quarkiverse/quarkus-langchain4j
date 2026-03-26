package io.quarkiverse.langchain4j.jaxrsclient;

import java.io.InputStream;
import java.security.KeyStore;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;

import org.jboss.resteasy.reactive.client.TlsConfig;
import org.jboss.resteasy.reactive.client.impl.ClientBuilderImpl;

import dev.langchain4j.exception.HttpException;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import io.quarkus.runtime.BlockingOperationControl;
import io.quarkus.tls.TlsConfiguration;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.vertx.core.net.KeyCertOptions;
import io.vertx.core.net.SSLOptions;
import io.vertx.core.net.TrustOptions;

public class JaxRsHttpClient implements HttpClient {

    private final Client delegate;

    public JaxRsHttpClient(JaxRsHttpClientBuilder builder) {
        ClientBuilderImpl clientBuilder = (ClientBuilderImpl) new ClientBuilderImpl()
                .connectTimeout(builder.connectTimeout().getSeconds(), TimeUnit.SECONDS)
                .readTimeout(builder.readTimeout().getSeconds(), TimeUnit.SECONDS);
        for (Object provider : builder.clientProviders()) {
            clientBuilder.register(provider);
        }
        if (builder.tlsConfiguration() != null) {
            TlsConfiguration tlsConfiguration = builder.tlsConfiguration();
            clientBuilder.tlsConfig(new TlsConfig() {
                @Override
                public KeyStore getKeyStore() {
                    return tlsConfiguration.getKeyStore();
                }

                @Override
                public KeyCertOptions getKeyStoreOptions() {
                    return tlsConfiguration.getKeyStoreOptions();
                }

                @Override
                public KeyStore getTrustStore() {
                    return tlsConfiguration.getTrustStore();
                }

                @Override
                public TrustOptions getTrustStoreOptions() {
                    return tlsConfiguration.getTrustStoreOptions();
                }

                @Override
                public SSLOptions getSSLOptions() {
                    return tlsConfiguration.getSSLOptions();
                }

                @Override
                public SSLContext createSSLContext() throws Exception {
                    return tlsConfiguration.createSSLContext();
                }

                @Override
                public Optional<String> getHostnameVerificationAlgorithm() {
                    return tlsConfiguration.getHostnameVerificationAlgorithm();
                }

                @Override
                public boolean usesSni() {
                    return tlsConfiguration.usesSni();
                }

                @Override
                public boolean isTrustAll() {
                    return tlsConfiguration.isTrustAll();
                }

                @Override
                public Optional<String> getName() {
                    return Optional.ofNullable(tlsConfiguration.getName());
                }
            });
        }
        delegate = clientBuilder.build();
    }

    public static JaxRsHttpClientBuilder builder() {
        return new JaxRsHttpClientBuilder();
    }

    @Override
    public SuccessfulHttpResponse execute(HttpRequest request) {
        WebTarget target = delegate.target(request.url());
        Invocation.Builder invocationBuilder = target.request();

        for (var headers : request.headers().entrySet()) {
            List<String> values = headers.getValue();
            if ((values != null) && (!values.isEmpty())) {
                for (String value : values) {
                    invocationBuilder.header(headers.getKey(), value);
                }
            }
        }

        Response response = switch (request.method()) {
            case GET -> invocationBuilder.get();
            case POST -> invocationBuilder.post(Entity.json(request.body()));
            case DELETE -> invocationBuilder.delete();
        };

        if (response.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
            throw new HttpException(response.getStatus(), response.readEntity(String.class));
        }

        return SuccessfulHttpResponse.builder()
                .statusCode(response.getStatus())
                .headers(response.getStringHeaders())
                .body(response.readEntity(String.class))
                .build();
    }

    @Override
    public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {
        if (!BlockingOperationControl.isBlockingAllowed()) {
            Infrastructure.getDefaultExecutor().execute(() -> executeBlocking(request, parser, listener));
            return;
        }
        executeBlocking(request, parser, listener);
    }

    private void executeBlocking(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {
        try {
            WebTarget target = delegate.target(request.url());
            Invocation.Builder invocationBuilder = target.request();

            for (var headers : request.headers().entrySet()) {
                List<String> values = headers.getValue();
                if ((values != null) && (!values.isEmpty())) {
                    for (String value : values) {
                        invocationBuilder.header(headers.getKey(), value);
                    }
                }
            }

            try (Response response = switch (request.method()) {
                case GET -> invocationBuilder.get();
                case POST -> invocationBuilder.post(Entity.json(request.body()));
                case DELETE -> invocationBuilder.delete();
            }) {
                if (response.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
                    HttpException httpException = new HttpException(response.getStatus(), response.readEntity(String.class));
                    listener.onError(httpException);
                    return;
                }

                listener.onOpen(SuccessfulHttpResponse.builder()
                        .statusCode(response.getStatus())
                        .headers(response.getStringHeaders())
                        .body(null)
                        .build());

                try (InputStream inputStream = response.readEntity(InputStream.class)) {
                    parser.parse(inputStream, listener);
                }
            }
        } catch (Throwable t) {
            listener.onError(t);
        } finally {
            listener.onClose();
        }
    }
}
