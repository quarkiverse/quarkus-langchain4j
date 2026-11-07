package io.quarkiverse.langchain4j.jaxrsclient;

import java.security.KeyStore;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.Response;

import org.jboss.resteasy.reactive.client.SseEvent;
import org.jboss.resteasy.reactive.client.TlsConfig;
import org.jboss.resteasy.reactive.client.impl.ClientBuilderImpl;
import org.jboss.resteasy.reactive.client.impl.MultiInvoker;

import dev.langchain4j.exception.HttpException;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.ServerSentEventContext;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import dev.langchain4j.http.client.sse.ServerSentEventParsingHandle;
import io.quarkus.tls.TlsConfiguration;
import io.smallrye.mutiny.subscription.MultiSubscriber;
import io.vertx.core.net.KeyCertOptions;
import io.vertx.core.net.SSLOptions;
import io.vertx.core.net.TrustOptions;

public class JaxRsHttpClient implements HttpClient {

    private static final GenericType<SseEvent<String>> SSE_EVENT_TYPE = new GenericType<>() {
    };

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

        String method = request.method().name();
        Entity<?> entity = (request.body() != null) ? Entity.json(request.body()) : null;

        MultiInvoker multiInvoker = invocationBuilder.rx(MultiInvoker.class);

        multiInvoker.method(method, entity, SSE_EVENT_TYPE)
                .subscribe().withSubscriber(new MultiSubscriber<SseEvent<String>>() {

                    private volatile Flow.Subscription subscription;

                    private final ServerSentEventParsingHandle parsingHandle = new ServerSentEventParsingHandle() {
                        private volatile boolean cancelled = false;

                        @Override
                        public void cancel() {
                            cancelled = true;
                            if (subscription != null) {
                                subscription.cancel();
                            }
                        }

                        @Override
                        public boolean isCancelled() {
                            return cancelled;
                        }
                    };

                    private final ServerSentEventContext context = new ServerSentEventContext(parsingHandle);

                    @Override
                    public void onSubscribe(Flow.Subscription subscription) {
                        this.subscription = subscription;
                        subscription.request(Long.MAX_VALUE);
                    }

                    @Override
                    public void onItem(SseEvent<String> event) {
                        if (!parsingHandle.isCancelled()) {
                            listener.onEvent(new ServerSentEvent(event.name(), (String) event.data()), context);
                        }
                    }

                    @Override
                    public void onFailure(Throwable throwable) {
                        listener.onError(throwable);
                        listener.onClose();
                    }

                    @Override
                    public void onCompletion() {
                        listener.onClose();
                    }
                });
    }
}
