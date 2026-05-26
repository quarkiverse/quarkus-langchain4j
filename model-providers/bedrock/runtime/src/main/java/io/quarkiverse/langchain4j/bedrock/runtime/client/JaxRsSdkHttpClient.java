package io.quarkiverse.langchain4j.bedrock.runtime.client;

import static io.quarkiverse.langchain4j.bedrock.runtime.client.JaxRsSdkHttpClientHelper.createAndPrepareInvocationBuilder;

import java.time.Duration;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Invocation;

import io.quarkus.tls.TlsConfiguration;
import software.amazon.awssdk.http.ExecutableHttpRequest;
import software.amazon.awssdk.http.HttpExecuteRequest;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.utils.AttributeMap;

public class JaxRsSdkHttpClient implements SdkHttpClient {

    private static final String CLIENT_NAME = "Quarkus LangChain4j";

    private final Client delegate;

    private JaxRsSdkHttpClient(Client client) {
        delegate = client;
    }

    @Override
    public ExecutableHttpRequest prepareRequest(final HttpExecuteRequest httpExecuteRequest) {
        final SdkHttpRequest request = httpExecuteRequest.httpRequest();

        Invocation.Builder invocationBuilder = createAndPrepareInvocationBuilder(delegate, request);

        return new JaxRsSdkHttpClientExecutable(invocationBuilder, httpExecuteRequest);
    }

    @Override
    public void close() {
        delegate.close();
    }

    @Override
    public String clientName() {
        return CLIENT_NAME;
    }

    public static Builder builder() {
        return new DefaultBuilder();
    }

    public interface Builder extends SdkHttpClient.Builder<JaxRsSdkHttpClient.Builder>, JaxRsSdkClientBuilder {

        Builder connectionTimeout(Duration timeout);

        Builder readTimeout(Duration readTimeout);

        Builder proxyAddress(String proxyAddress);

        Builder proxyUser(String proxyUser);

        Builder proxyPassword(String proxyPassword);

        Builder nonProxyHosts(String nonProxyHosts);

        Builder disableContextualErrorMessages(Boolean disableContextualErrorMessages);

        Builder connectionTTL(int connectionTTL);

        Builder connectionPoolSize(int connectionPoolSize);

        Builder keepAliveEnabled(boolean keepAliveEnabled);

        Builder hostnameVerifier(String hostnameVerifier);

        Builder verifyHost(boolean verifyHost);

        Builder trustStore(String trustStore);

        Builder trustStorePassword(String trustStorePassword);

        Builder trustStoreType(String trustStoreType);

        Builder keyStore(String keyStore);

        Builder keyStorePassword(String keyStorePassword);

        Builder keyStoreType(String keyStoreType);

        Builder tlsConfig(TlsConfiguration tlsConfig);

    }

    private static class DefaultBuilder extends DefaultJaxRsSdkClientBuilder implements Builder {

        @Override
        public Builder connectionTimeout(Duration timeout) {
            super.connectionTimeout(timeout);
            return this;
        }

        @Override
        public Builder readTimeout(Duration readTimeout) {
            super.readTimeout(readTimeout);
            return this;
        }

        @Override
        public Builder proxyAddress(String proxyAddress) {
            super.proxyAddress(proxyAddress);
            return this;
        }

        @Override
        public Builder proxyUser(String proxyUser) {
            super.proxyUser(proxyUser);
            return this;
        }

        @Override
        public Builder proxyPassword(String proxyPassword) {
            super.proxyPassword(proxyPassword);
            return this;
        }

        @Override
        public Builder nonProxyHosts(final String nonProxyHosts) {
            super.nonProxyHosts(nonProxyHosts);
            return this;
        }

        @Override
        public Builder disableContextualErrorMessages(Boolean disableContextualErrorMessages) {
            super.disableContextualErrorMessages(disableContextualErrorMessages);
            return this;
        }

        @Override
        public Builder connectionTTL(int connectionTTL) {
            super.connectionTTL(connectionTTL);
            return this;
        }

        @Override
        public Builder connectionPoolSize(int connectionPoolSize) {
            super.connectionPoolSize(connectionPoolSize);
            return this;
        }

        @Override
        public Builder keepAliveEnabled(boolean keepAliveEnabled) {
            super.keepAliveEnabled(keepAliveEnabled);
            return this;
        }

        @Override
        public Builder hostnameVerifier(String hostnameVerifier) {
            super.hostnameVerifier(hostnameVerifier);
            return this;
        }

        @Override
        public Builder verifyHost(final boolean verifyHost) {
            super.verifyHost(verifyHost);
            return this;
        }

        @Override
        public Builder trustStore(String trustStore) {
            super.trustStore(trustStore);
            return this;
        }

        @Override
        public Builder trustStorePassword(String trustStorePassword) {
            super.trustStorePassword(trustStorePassword);
            return this;
        }

        @Override
        public Builder trustStoreType(String trustStoreType) {
            super.trustStoreType(trustStoreType);
            return this;
        }

        @Override
        public Builder keyStore(String keyStore) {
            super.keyStore(keyStore);
            return this;
        }

        @Override
        public Builder keyStorePassword(String keyStorePassword) {
            super.keyStorePassword(keyStorePassword);
            return this;
        }

        @Override
        public Builder keyStoreType(String keyStoreType) {
            super.keyStoreType(keyStoreType);
            return this;
        }

        @Override
        public Builder tlsConfig(TlsConfiguration tlsConfig) {
            super.tlsConfig(tlsConfig);
            return this;
        }

        @Override
        public SdkHttpClient buildWithDefaults(AttributeMap serviceDefaults) {
            return new JaxRsSdkHttpClient(this.buildClientWithDefaults(serviceDefaults));
        }
    }
}
