package io.quarkiverse.langchain4j.infinispan.runtime;

import java.util.function.Function;
import java.util.function.Supplier;

import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.protostream.MessageMarshaller;

import io.quarkiverse.langchain4j.infinispan.InfinispanEmbeddingStore;
import io.quarkiverse.langchain4j.infinispan.SchemaAndMarshallerProducer;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.infinispan.client.InfinispanClientName;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;

/**
 * Quarkus recorder that creates the {@link InfinispanEmbeddingStore} bean at runtime,
 * wiring the Infinispan cache manager and configuration together.
 */
@Recorder
public class InfinispanEmbeddingStoreRecorder {
    private final RuntimeValue<InfinispanEmbeddingStoreConfig> runtimeConfig;

    public InfinispanEmbeddingStoreRecorder(RuntimeValue<InfinispanEmbeddingStoreConfig> runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    public Function<SyntheticCreationalContext<InfinispanEmbeddingStore>, InfinispanEmbeddingStore> embeddingStoreFunction(
            String clientName, String storeName) {
        return new Function<>() {
            @Override
            public InfinispanEmbeddingStore apply(SyntheticCreationalContext<InfinispanEmbeddingStore> context) {
                InfinispanEmbeddingStore.Builder builder = new InfinispanEmbeddingStore.Builder();
                RemoteCacheManager cacheManager;
                if (clientName == null) {
                    cacheManager = context.getInjectedReference(RemoteCacheManager.class);
                } else {
                    cacheManager = context.getInjectedReference(RemoteCacheManager.class,
                            new InfinispanClientName.Literal(clientName));
                }
                builder.cacheManager(cacheManager);
                InfinispanEmbeddingStoreConfig config = runtimeConfig.getValue();

                InfinispanStoreRuntimeConfig storeConfig = NamedConfigUtil.isDefault(storeName)
                        ? config.defaultConfig()
                        : config.namedConfig().getOrDefault(storeName, config.defaultConfig());

                builder.schema(new InfinispanSchema(
                        storeConfig.cacheName(),
                        storeConfig.dimension(),
                        storeConfig.distance(),
                        storeConfig.similarity(),
                        storeConfig.createCache(),
                        storeConfig.cacheConfig().orElse(null)));
                return builder.build();
            }
        };
    }

    public Supplier<MessageMarshaller> itemMarshallerSupplier(String storeName) {
        return new Supplier<MessageMarshaller>() {
            @Override
            public MessageMarshaller get() {
                InfinispanStoreRuntimeConfig storeConfig = resolveStoreConfig(storeName);
                return new LangchainItemMarshaller(storeConfig.dimension());
            }
        };
    }

    public Supplier<MessageMarshaller> metadataMarshallerSupplier(String storeName) {
        return new Supplier<MessageMarshaller>() {
            @Override
            public MessageMarshaller get() {
                InfinispanStoreRuntimeConfig storeConfig = resolveStoreConfig(storeName);
                String typeName = SchemaAndMarshallerProducer.LANGCHAIN_METADATA + storeConfig.dimension();
                return new LangchainMetadataMarshaller(typeName);
            }
        };
    }

    private InfinispanStoreRuntimeConfig resolveStoreConfig(String storeName) {
        InfinispanEmbeddingStoreConfig config = runtimeConfig.getValue();
        return NamedConfigUtil.isDefault(storeName)
                ? config.defaultConfig()
                : config.namedConfig().getOrDefault(storeName, config.defaultConfig());
    }
}
