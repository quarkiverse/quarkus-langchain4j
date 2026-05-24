package io.quarkiverse.langchain4j.oracle.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import jakarta.enterprise.inject.Default;

import dev.langchain4j.store.embedding.oracle.CreateOption;
import dev.langchain4j.store.embedding.oracle.EmbeddingTable;
import dev.langchain4j.store.embedding.oracle.IVFIndexBuilder;
import dev.langchain4j.store.embedding.oracle.Index;
import dev.langchain4j.store.embedding.oracle.JSONIndexBuilder;
import dev.langchain4j.store.embedding.oracle.OracleEmbeddingStore;
import io.agroal.api.AgroalDataSource;
import io.quarkiverse.langchain4j.oracle.QuarkusOracleEmbeddingStore;
import io.quarkiverse.langchain4j.oracle.runtime.OracleStoreRuntimeConfig.MetadataIndexConfig;
import io.quarkiverse.langchain4j.oracle.runtime.OracleStoreRuntimeConfig.MetadataIndexKeyConfig;
import io.quarkiverse.langchain4j.oracle.runtime.OracleStoreRuntimeConfig.VectorIndexConfig;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.agroal.DataSource.DataSourceLiteral;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class OracleEmbeddingStoreRecorder {

    private final RuntimeValue<OracleEmbeddingStoreConfig> runtimeConfig;

    public OracleEmbeddingStoreRecorder(RuntimeValue<OracleEmbeddingStoreConfig> runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    public Function<SyntheticCreationalContext<QuarkusOracleEmbeddingStore>, QuarkusOracleEmbeddingStore> embeddingStoreFunction(
            String datasourceName, String storeName) {
        return new Function<>() {
            @Override
            public QuarkusOracleEmbeddingStore apply(SyntheticCreationalContext<QuarkusOracleEmbeddingStore> context) {
                AgroalDataSource dataSource;
                if (datasourceName != null && !NamedConfigUtil.isDefault(datasourceName)) {
                    dataSource = context.getInjectedReference(AgroalDataSource.class, new DataSourceLiteral(datasourceName));
                } else {
                    dataSource = context.getInjectedReference(AgroalDataSource.class, Default.Literal.INSTANCE);
                }

                dataSource.flush(AgroalDataSource.FlushMode.GRACEFUL);

                OracleStoreRuntimeConfig storeConfig = correspondingStoreConfig(storeName);

                EmbeddingTable.Builder tableBuilder = EmbeddingTable.builder()
                        .name(storeConfig.table())
                        .createOption(storeConfig.createOption());

                storeConfig.idColumn().ifPresent(tableBuilder::idColumn);
                storeConfig.embeddingColumn().ifPresent(tableBuilder::embeddingColumn);
                storeConfig.textColumn().ifPresent(tableBuilder::textColumn);
                storeConfig.metadataColumn().ifPresent(tableBuilder::metadataColumn);

                OracleEmbeddingStore.Builder builder = OracleEmbeddingStore.builder()
                        .dataSource(dataSource)
                        .embeddingTable(tableBuilder.build());

                if (storeConfig.exactSearch()) {
                    builder.exactSearch(true);
                }

                List<Index> indexes = new ArrayList<>();

                VectorIndexConfig vectorIndexConfig = storeConfig.vectorIndex();
                if (vectorIndexConfig.createOption() != CreateOption.CREATE_NONE) {
                    IVFIndexBuilder ivfBuilder = Index.ivfIndexBuilder()
                            .createOption(vectorIndexConfig.createOption());
                    if (vectorIndexConfig.targetAccuracy() > 0) {
                        ivfBuilder.targetAccuracy(vectorIndexConfig.targetAccuracy());
                    }
                    if (vectorIndexConfig.degreeOfParallelism() > 0) {
                        ivfBuilder.degreeOfParallelism(vectorIndexConfig.degreeOfParallelism());
                    }
                    if (vectorIndexConfig.neighborPartitions() > 0) {
                        ivfBuilder.neighborPartitions(vectorIndexConfig.neighborPartitions());
                    }
                    if (vectorIndexConfig.samplePerPartition() > 0) {
                        ivfBuilder.samplePerPartition(vectorIndexConfig.samplePerPartition());
                    }
                    if (vectorIndexConfig.minVectorsPerPartition() > 0) {
                        ivfBuilder.minVectorsPerPartition(vectorIndexConfig.minVectorsPerPartition());
                    }
                    indexes.add(ivfBuilder.build());
                }

                List<MetadataIndexConfig> metadataIndexConfigs = storeConfig.metadataIndexes();
                for (MetadataIndexConfig metadataIndexConfig : metadataIndexConfigs) {
                    if (metadataIndexConfig.createOption() == CreateOption.CREATE_NONE) {
                        continue;
                    }
                    JSONIndexBuilder jsonBuilder = Index.jsonIndexBuilder()
                            .createOption(metadataIndexConfig.createOption());
                    if (metadataIndexConfig.unique()) {
                        jsonBuilder.isUnique(true);
                    }
                    if (metadataIndexConfig.bitmap()) {
                        jsonBuilder.isBitmap(true);
                    }
                    for (MetadataIndexKeyConfig keyConfig : metadataIndexConfig.keys()) {
                        jsonBuilder.key(keyConfig.key(), resolveKeyType(keyConfig.type()),
                                JSONIndexBuilder.Order.valueOf(keyConfig.order()));
                    }
                    indexes.add(jsonBuilder.build());
                }

                if (!indexes.isEmpty()) {
                    builder.index(indexes.toArray(new Index[0]));
                }

                return new QuarkusOracleEmbeddingStore(builder.build());
            }
        };
    }

    private static Class<?> resolveKeyType(String type) {
        return switch (type.toUpperCase()) {
            case "INTEGER" -> Integer.class;
            case "LONG" -> Long.class;
            case "FLOAT" -> Float.class;
            case "DOUBLE" -> Double.class;
            default -> String.class;
        };
    }

    private OracleStoreRuntimeConfig correspondingStoreConfig(String storeName) {
        OracleStoreRuntimeConfig storeConfig;
        if (NamedConfigUtil.isDefault(storeName)) {
            storeConfig = runtimeConfig.getValue().defaultConfig();
        } else {
            storeConfig = runtimeConfig.getValue().namedConfig().get(storeName);
        }
        return storeConfig;
    }
}
