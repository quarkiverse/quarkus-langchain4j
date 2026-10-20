package io.quarkiverse.langchain4j.mockapi.internal;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;

/**
 * Gives the Bedrock clients fixed credentials for the mock, so the AWS SDK never runs its default credentials provider
 * chain (environment, profile files, EC2 instance metadata). The Bedrock recorder looks a credentials provider up by
 * bean name, and the mock fills in {@code aws.credentials-provider} with {@value ProviderSpecs#AWS_CREDENTIALS_BEAN}
 * unless the application configured one. Registered only when the Bedrock provider is present.
 */
@ApplicationScoped
@Named(ProviderSpecs.AWS_CREDENTIALS_BEAN)
public class MockAwsCredentialsProvider implements AwsCredentialsProvider {

    private static final AwsCredentials CREDENTIALS = AwsBasicCredentials.create("mock-access-key", "mock-secret-key");

    @Override
    public AwsCredentials resolveCredentials() {
        return CREDENTIALS;
    }
}
