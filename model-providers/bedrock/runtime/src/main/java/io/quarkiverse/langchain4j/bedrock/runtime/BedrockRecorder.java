package io.quarkiverse.langchain4j.bedrock.runtime;

import static io.quarkiverse.langchain4j.runtime.OptionalUtil.firstOrDefault;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Consumer;
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
import io.quarkiverse.langchain4j.bedrock.runtime.config.AwsClientConfig;
import io.quarkiverse.langchain4j.bedrock.runtime.config.LangChain4jBedrockConfig;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkiverse.langchain4j.runtime.config.LangChain4jConfig;
import io.quarkus.arc.Arc;
import io.quarkus.runtime.annotations.Recorder;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.retries.api.RetryStrategy;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

@Recorder
public class BedrockRecorder {

    public Supplier<ChatLanguageModel> chatModel(LangChain4jBedrockConfig runtimeConfig, String configName,
            final LangChain4jConfig rootConfig) {
        LangChain4jBedrockConfig.BedrockConfig config = correspondingBedrockConfig(runtimeConfig, configName);

        if (config.enableIntegration()) {
            var modelConfig = config.chatModel();

            var paramBuilder = ChatRequestParameters.builder()
                    .maxOutputTokens(modelConfig.maxTokens());

            if (modelConfig.temperature().isPresent()) {
                paramBuilder.temperature(modelConfig.temperature().getAsDouble());
            }

            if (modelConfig.topP().isPresent()) {
                paramBuilder.topP(modelConfig.topP().getAsDouble());
            }

            if (modelConfig.topK().isPresent()) {
                paramBuilder.topK(modelConfig.topK().getAsInt());
            }

            if (modelConfig.stopSequences().isPresent()) {
                paramBuilder.stopSequences(modelConfig.stopSequences().get().toArray(new String[0]));
            }

            var clientBuilder = BedrockRuntimeClient.builder();

            clientBuilder.httpClient(JaxRsSdkHttpClientFactory.createSync(modelConfig.client(), config.client(), rootConfig));

            configureClient(clientBuilder, modelConfig, config);

            var builder = BedrockChatModel.builder()
                    .modelId(modelConfig.modelId().orElse("us.amazon.nova-lite-v1:0"))
                    .client(clientBuilder.build())
                    .defaultRequestParameters(paramBuilder.build());

            return new Supplier<ChatLanguageModel>() {
                @Override
                public ChatLanguageModel get() {
                    return builder.build();
                }
            };
        } else {
            return new Supplier<ChatLanguageModel>() {
                @Override
                public ChatLanguageModel get() {
                    return new DisabledChatLanguageModel();
                }
            };
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
            final String configName, final LangChain4jConfig rootConfig) {
        LangChain4jBedrockConfig.BedrockConfig config = correspondingBedrockConfig(runtimeConfig, configName);

        if (config.enableIntegration()) {
            var modelConfig = config.chatModel();

            var clientBuilder = BedrockRuntimeAsyncClient.builder();

            clientBuilder.httpClient(JaxRsSdkHttpClientFactory.createAsync(modelConfig.client(), config.client(), rootConfig));

            configureClient(clientBuilder, modelConfig, config);

            var modelId = modelConfig.modelId().orElse("anthropic.claude-v2");

            Supplier<StreamingChatLanguageModel> supplier;
            if (modelId.startsWith("anthropic")) {

                var builder = BedrockAnthropicStreamingChatModel.builder()
                        .model(modelConfig.modelId().orElse("anthropic.claude-v2"))
                        .asyncClient(clientBuilder.build())
                        .maxTokens(modelConfig.maxTokens());

                if (modelConfig.temperature().isPresent()) {
                    builder.temperature((float) modelConfig.temperature().getAsDouble());
                }

                if (modelConfig.topP().isPresent()) {
                    builder.topP((float) modelConfig.topP().getAsDouble());
                }

                if (modelConfig.topK().isPresent()) {
                    builder.topK(modelConfig.topK().getAsInt());
                }

                if (modelConfig.stopSequences().isPresent()) {
                    builder.stopSequences(modelConfig.stopSequences().get().toArray(new String[0]));
                }

                supplier = new Supplier<StreamingChatLanguageModel>() {
                    @Override
                    public StreamingChatLanguageModel get() {
                        return builder.build();
                    }
                };
            } else {
                var client = clientBuilder.build();
                supplier = new Supplier<StreamingChatLanguageModel>() {
                    @Override
                    public StreamingChatLanguageModel get() {
                        return new BedrockConverseStreamingChatModel(client, modelId, modelConfig);
                    }
                };
            }

            return supplier;
        } else {
            return new Supplier<StreamingChatLanguageModel>() {
                @Override
                public StreamingChatLanguageModel get() {
                    return new DisabledStreamingChatLanguageModel();
                }
            };
        }
    }

    public Supplier<EmbeddingModel> embeddingModel(final LangChain4jBedrockConfig runtimeConfig,
            final String configName, final LangChain4jConfig rootConfig) {
        LangChain4jBedrockConfig.BedrockConfig config = correspondingBedrockConfig(runtimeConfig, configName);

        if (config.enableIntegration()) {
            var modelConfig = config.embeddingModel();

            var clientBuilder = BedrockRuntimeClient.builder(); //NOSONAR creds can be specified later

            clientBuilder.httpClient(JaxRsSdkHttpClientFactory.createSync(modelConfig.client(), config.client(), rootConfig));

            configureClient(clientBuilder, modelConfig, config);

            var modelId = modelConfig.modelId();

            Supplier<EmbeddingModel> supplier;
            if (modelId.contains("cohere")) {
                var builder = BedrockCohereEmbeddingModel.builder()
                        .model(modelId)
                        .client(clientBuilder.build());

                if (modelConfig.cohere().inputType().isPresent()) {
                    builder.inputType(modelConfig.cohere().inputType().get());
                }

                if (modelConfig.cohere().truncate().isPresent()) {
                    builder.truncate(modelConfig.cohere().truncate().get());
                }

                supplier = new Supplier<EmbeddingModel>() {
                    @Override
                    public EmbeddingModel get() {
                        return builder.build();
                    }
                };
            } else {
                var builder = BedrockTitanEmbeddingModel.builder()
                        .model(modelId)
                        .client(clientBuilder.build());

                if (modelConfig.titan().dimensions().isPresent()) {
                    builder.dimensions(modelConfig.titan().dimensions().getAsInt());
                }

                if (modelConfig.titan().normalize().isPresent()) {
                    builder.normalize(modelConfig.titan().normalize().get());
                }

                supplier = new Supplier<EmbeddingModel>() {
                    @Override
                    public EmbeddingModel get() {
                        return builder.build();
                    }
                };
            }

            return supplier;
        } else {
            return new Supplier<EmbeddingModel>() {
                @Override
                public EmbeddingModel get() {
                    return new DisabledEmbeddingModel();
                }
            };
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

    private void configureClient(
            final AwsClientBuilder<?, ?> awsClientBuilder,
            final AwsClientConfig modelConfig,
            final LangChain4jBedrockConfig.BedrockConfig bedrockConfig) {
        var overrideConfig = new Consumer<ClientOverrideConfiguration.Builder>() {
            @Override
            public void accept(final ClientOverrideConfiguration.Builder builder) {
                builder.retryStrategy(new Consumer<RetryStrategy.Builder<?, ?>>() {
                    @Override
                    public void accept(final RetryStrategy.Builder<?, ?> retryStrategyBuilder) {
                        var maxRetries = firstOrDefault(3, modelConfig.aws().maxRetries(), bedrockConfig.aws().maxRetries());
                        retryStrategyBuilder.maxAttempts(maxRetries);
                    }
                });

                builder.apiCallTimeout(firstOrDefault(Duration.ofSeconds(10),
                        modelConfig.aws().apiCallTimeout(), bedrockConfig.aws().apiCallTimeout()));
                var logRequest = firstOrDefault(false, modelConfig.logRequests(), bedrockConfig.logRequests());
                var logResponse = firstOrDefault(false, modelConfig.logResponses(), bedrockConfig.logResponses());
                if (logRequest || logResponse) {
                    builder.addExecutionInterceptor(
                            new AwsLoggingInterceptor(logRequest, logResponse,
                                    firstOrDefault(false, modelConfig.logBody(), bedrockConfig.logBody())));
                }
            }
        };

        awsClientBuilder.overrideConfiguration(overrideConfig);

        var region = Optional.ofNullable(firstOrDefault(null, modelConfig.aws().region(), bedrockConfig.aws().region()));
        if (region.isPresent()) {
            awsClientBuilder.region(Region.of(region.get()));
        }

        var endpointOverride = Optional
                .ofNullable(firstOrDefault(null, modelConfig.aws().endpointOverride(), bedrockConfig.aws().endpointOverride()));
        if (endpointOverride.isPresent()) {
            awsClientBuilder.endpointOverride(URI.create(endpointOverride.get()));
        }

        var credentialsProvider = Optional.ofNullable(
                firstOrDefault(null, modelConfig.aws().credentialsProvider(), bedrockConfig.aws().credentialsProvider()));
        if (credentialsProvider.isPresent()) {
            awsClientBuilder.credentialsProvider(getCredentialsProvider(credentialsProvider.get()));
        }
    }
}
