package io.quarkiverse.langchain4j.hibernate.runtime;

import static org.hibernate.cfg.PersistenceSettings.PERSISTENCE_UNIT_NAME;
import static org.hibernate.engine.config.spi.StandardConverters.INTEGER;
import static org.hibernate.engine.config.spi.StandardConverters.STRING;

import org.hibernate.boot.ResourceStreamLocator;
import org.hibernate.boot.spi.AdditionalMappingContributions;
import org.hibernate.boot.spi.AdditionalMappingContributor;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.engine.config.spi.ConfigurationService;
import org.hibernate.mapping.PersistentClass;

import dev.langchain4j.store.embedding.hibernate.EmbeddingEntity;

public class DynamicEmbeddingStoreAdditionalMappingContributor implements AdditionalMappingContributor {

    public static final String DEFAULT_DYNAMIC_PU_NAME = "<langchain4j-hibernate-dynamic-default>";
    public static final String TABLE_CONFIGURATION = "langchain4j.hibernate.table";
    public static final String DIMENSION_CONFIGURATION = "langchain4j.hibernate.dimension";
    private static final String EMBEDDING_ENTITY = EmbeddingEntity.class.getName();

    @Override
    public void contribute(
            final AdditionalMappingContributions contributions,
            final InFlightMetadataCollector metadata,
            final ResourceStreamLocator resourceStreamLocator,
            final MetadataBuildingContext buildingContext) {
        final ConfigurationService configurationService = buildingContext.getBootstrapContext()
                .getConfigurationService();
        final String persistenceUnitName = configurationService.getSetting(PERSISTENCE_UNIT_NAME, STRING);
        if (DEFAULT_DYNAMIC_PU_NAME.equals(persistenceUnitName)) {
            final String table = configurationService.getSetting(TABLE_CONFIGURATION, STRING);
            final Integer dimension = configurationService.getSetting(DIMENSION_CONFIGURATION, INTEGER);
            final PersistentClass entityBinding = metadata.getEntityBinding(EMBEDDING_ENTITY);
            entityBinding.getTable().setName(table);
            entityBinding.getProperty("embedding")
                    .getValue()
                    .getColumns()
                    .get(0)
                    .setArrayLength(dimension);
        }
    }

    @Override
    public String getContributorName() {
        return "Quarkus Langchain4j Hibernate DynamicEmbeddingStore";
    }
}
