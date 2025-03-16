package io.quarkiverse.langchain4j.bedrock.deployment;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

@ApplicationScoped
@Named("TestCredentialsProvider")
public class TestCredentialsProvider implements AwsCredentialsProvider {

    private static final StaticCredentialsProvider provider = StaticCredentialsProvider
            .create(AwsBasicCredentials.create("test", "test"));

    @Override
    public AwsCredentials resolveCredentials() {
        return provider.resolveCredentials();
    }
}
