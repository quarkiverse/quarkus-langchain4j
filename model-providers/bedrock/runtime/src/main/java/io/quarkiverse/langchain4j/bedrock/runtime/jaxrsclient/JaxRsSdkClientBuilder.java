package io.quarkiverse.langchain4j.bedrock.runtime.jaxrsclient;

import java.time.Duration;

import io.quarkus.tls.TlsConfiguration;

public interface JaxRsSdkClientBuilder {

    JaxRsSdkClientBuilder connectionTimeout(Duration timeout);

    JaxRsSdkClientBuilder readTimeout(Duration readTimeout);

    JaxRsSdkClientBuilder proxyAddress(String proxyAddress);

    JaxRsSdkClientBuilder proxyUser(String proxyUser);

    JaxRsSdkClientBuilder proxyPassword(String proxyPassword);

    JaxRsSdkClientBuilder nonProxyHosts(String nonProxyHosts);

    JaxRsSdkClientBuilder disableContextualErrorMessages(Boolean disableContextualErrorMessages);

    JaxRsSdkClientBuilder connectionTTL(int connectionTTL);

    JaxRsSdkClientBuilder connectionPoolSize(int connectionPoolSize);

    JaxRsSdkClientBuilder keepAliveEnabled(boolean keepAliveEnabled);

    JaxRsSdkClientBuilder hostnameVerifier(String hostnameVerifier);

    JaxRsSdkClientBuilder verifyHost(boolean verifyHost);

    JaxRsSdkClientBuilder trustStore(String trustStore);

    JaxRsSdkClientBuilder trustStorePassword(String trustStorePassword);

    JaxRsSdkClientBuilder trustStoreType(String trustStoreType);

    JaxRsSdkClientBuilder keyStore(String keyStore);

    JaxRsSdkClientBuilder keyStorePassword(String keyStorePassword);

    JaxRsSdkClientBuilder keyStoreType(String keyStoreType);

    JaxRsSdkClientBuilder tlsConfig(TlsConfiguration tlsConfig);
}
