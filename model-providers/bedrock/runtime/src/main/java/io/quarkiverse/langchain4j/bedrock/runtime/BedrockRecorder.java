package io.quarkiverse.langchain4j.bedrock.runtime;

import static io.quarkiverse.langchain4j.runtime.OptionalUtil.firstOrDefault;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

import dev.langchain4j.model.bedrock.BedrockAnthropicStreamingChatModel;
import dev.langchain4j.model.bedrock.BedrockChatModel;
import dev.langchain4j.model.bedrock.BedrockCohereEmbeddingModel;
import dev.langchain4j.model.bedrock.BedrockTitanEmbeddingModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.DisabledChatLanguageModel;
import dev.langchain4j.model.chat.DisabledStreamingChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.embedding.DisabledEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.quarkiverse.langchain4j.bedrock.runtime.config.LangChain4jBedrockConfig;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.arc.Arc;
import io.quarkus.runtime.annotations.Recorder;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

@Recorder
public class BedrockRecorder {

    public Supplier<ChatLanguageModel> chatModel(LangChain4jBedrockConfig runtimeConfig, String configName) {
        LangChain4jBedrockConfig.BedrockConfig config = correspondingBedrockConfig(runtimeConfig, configName);

        if (config.enableIntegration()) {
            var modelConfig = config.chatModel();

            var paramBuilder = ChatRequestParameters.builder()
                    .maxOutputTokens(modelConfig.maxTokens());

            modelConfig.temperature().ifPresent(paramBuilder::temperature);
            modelConfig.topP().ifPresent(paramBuilder::topP);
            modelConfig.topK().ifPresent(paramBuilder::topK);
            modelConfig.stopSequences().ifPresent(paramBuilder::stopSequences);

            var clientBuilder = BedrockRuntimeClient.builder(); //NOSONAR creds can be specified later

            modelConfig.client().region().map(Region::of).ifPresent(clientBuilder::region);
            modelConfig.client().endpointOverride().map(URI::create).ifPresent(clientBuilder::endpointOverride);
            modelConfig.client().credentialsProvider().map(this::getCredentialsProvider)
                    .ifPresent(clientBuilder::credentialsProvider);

            clientBuilder.overrideConfiguration(b -> configureClient(
                    new AwsClientConfigProvider(
                            b,
                            modelConfig.client().maxRetries(),
                            firstOrDefault(Duration.ofSeconds(10), modelConfig.client().timeout(), config.timeout()),
                            firstOrDefault(false, modelConfig.client().logRequests(), config.logRequests()),
                            firstOrDefault(false, modelConfig.client().logResponses(), config.logResponses()),
                            firstOrDefault(false, modelConfig.client().logBody(), config.logBody()))));

            var builder = BedrockChatModel.builder()
                    .modelId(modelConfig.modelId().orElse("us.amazon.nova-lite-v1:0"))
                    .client(clientBuilder.build())
                    .defaultRequestParameters(paramBuilder.build());

            return builder::build;
        } else {
            return DisabledChatLanguageModel::new;
        }
    }

    private AwsCredentialsProvider getCredentialsProvider(final String beanName) {
        var cp = Arc.container().instance(beanName).get();

        if (cp == null) {
            throw new IllegalArgumentException(
                    String.format("Cannot find the specified credentials provider by bean name '%s'", beanName));
        }

        if (cp instanceof AwsCredentialsProvider awsCp) {
            return awsCp;
        }
        throw new IllegalArgumentException(
                String.format("Configured credentials provider '%s' is not instance of AwsCredentialsProvider",
                        cp.getClass().getName()));
    }

    public Supplier<StreamingChatLanguageModel> streamingChatModel(final LangChain4jBedrockConfig runtimeConfig,
            final String configName) {
        LangChain4jBedrockConfig.BedrockConfig config = correspondingBedrockConfig(runtimeConfig, configName);

        if (config.enableIntegration()) {
            var modelConfig = config.chatModel();

            var clientBuilder = BedrockRuntimeAsyncClient.builder(); //NOSONAR creds can be specified later

            clientBuilder.overrideConfiguration(b -> configureClient(
                    new AwsClientConfigProvider(
                            b,
                            modelConfig.client().maxRetries(),
                            firstOrDefault(Duration.ofSeconds(10), modelConfig.client().timeout(), config.timeout()),
                            firstOrDefault(false, modelConfig.client().logRequests(), config.logRequests()),
                            firstOrDefault(false, modelConfig.client().logResponses(), config.logResponses()),
                            firstOrDefault(false, modelConfig.client().logBody(), config.logBody()))));

            modelConfig.client().region().map(Region::of).ifPresent(clientBuilder::region);
            modelConfig.client().endpointOverride().map(URI::create).ifPresent(clientBuilder::endpointOverride);
            modelConfig.client().credentialsProvider().map(this::getCredentialsProvider)
                    .ifPresent(clientBuilder::credentialsProvider);

            var modelId = modelConfig.modelId().orElse("anthropic.claude-v2");

            Supplier<StreamingChatLanguageModel> supplier;
            if (modelId.startsWith("anthropic")) {

                var builder = BedrockAnthropicStreamingChatModel.builder()
                        .model(modelConfig.modelId().orElse("anthropic.claude-v2"))
                        .asyncClient(clientBuilder.build())
                        .maxTokens(modelConfig.maxTokens());

                modelConfig.temperature().ifPresent(builder::temperature);
                modelConfig.topP().ifPresent(d -> builder.topP((float) d));
                modelConfig.topK().ifPresent(builder::topK);
                modelConfig.stopSequences().map(s -> s.toArray(new String[0])).ifPresent(builder::stopSequences);

                supplier = builder::build;
            } else {
                var client = clientBuilder.build();
                supplier = () -> new BedrockConverseStreamingChatModel(client, modelId, modelConfig);
            }

            return supplier;
        } else {
            return DisabledStreamingChatLanguageModel::new;
        }
    }

    public Supplier<EmbeddingModel> embeddingModel(final LangChain4jBedrockConfig runtimeConfig,
            final String configName) {
        LangChain4jBedrockConfig.BedrockConfig config = correspondingBedrockConfig(runtimeConfig, configName);

        if (config.enableIntegration()) {
            var modelConfig = config.embeddingModel();

            var clientBuilder = BedrockRuntimeClient.builder(); //NOSONAR creds can be specified later

            clientBuilder.overrideConfiguration(b -> configureClient(
                    new AwsClientConfigProvider(
                            b,
                            modelConfig.client().maxRetries(),
                            firstOrDefault(Duration.ofSeconds(10), modelConfig.client().timeout(), config.timeout()),
                            firstOrDefault(false, modelConfig.client().logRequests(), config.logRequests()),
                            firstOrDefault(false, modelConfig.client().logResponses(), config.logResponses()),
                            firstOrDefault(false, modelConfig.client().logBody(), config.logBody()))));

            modelConfig.client().region().map(Region::of).ifPresent(clientBuilder::region);
            modelConfig.client().endpointOverride().map(URI::create).ifPresent(clientBuilder::endpointOverride);
            modelConfig.client().credentialsProvider().map(this::getCredentialsProvider)
                    .ifPresent(clientBuilder::credentialsProvider);

            var modelId = modelConfig.modelId();

            Supplier<EmbeddingModel> supplier;
            if (modelId.contains("cohere")) {
                var builder = BedrockCohereEmbeddingModel.builder()
                        .model(modelId)
                        .client(clientBuilder.build());

                modelConfig.cohere().inputType().ifPresent(builder::inputType);
                modelConfig.cohere().truncate().ifPresent(builder::truncate);

                supplier = builder::build;
            } else {
                var builder = BedrockTitanEmbeddingModel.builder()
                        .model(modelId)
                        .client(clientBuilder.build());

                modelConfig.titan().dimensions().ifPresent(builder::dimensions);
                modelConfig.titan().normalize().ifPresent(builder::normalize);

                supplier = builder::build;
            }

            return supplier;
        } else {
            return DisabledEmbeddingModel::new;
        }
    }

    private LangChain4jBedrockConfig.BedrockConfig correspondingBedrockConfig(LangChain4jBedrockConfig runtimeConfig,
            String configName) {
        LangChain4jBedrockConfig.BedrockConfig config;
        if (NamedConfigUtil.isDefault(configName)) {
            config = runtimeConfig.defaultConfig();
        } else {
            config = runtimeConfig.namedConfig().get(configName);
        }
        return config;
    }

    private void configureClient(final AwsClientConfigProvider configProvider) {
        var builder = configProvider.builder();
        builder.retryStrategy(r -> configProvider.maxRetries().ifPresent(r::maxAttempts));
        builder.apiCallTimeout(configProvider.timeout());
        var logRequest = configProvider.logRequests();
        var logResponse = configProvider.logResponses();
        if (logRequest || logResponse) {
            builder.addExecutionInterceptor(
                    new AwsLoggingInterceptor(logRequest, logResponse, configProvider.logBody()));
        }
    }

    private record AwsClientConfigProvider(
            ClientOverrideConfiguration.Builder builder,
            Optional<Integer> maxRetries,
            Duration timeout,
            boolean logRequests,
            boolean logResponses,
            boolean logBody) {
    }
}
