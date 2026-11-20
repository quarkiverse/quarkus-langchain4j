package io.quarkiverse.langchain4j.hibernate.milvus.deployment;

import static io.quarkus.datasource.deployment.spi.DatabaseDefaultSetupConfig.DEFAULT_DATABASE_NAME;
import static io.quarkus.datasource.deployment.spi.DatabaseDefaultSetupConfig.DEFAULT_DATABASE_PASSWORD;
import static io.quarkus.datasource.deployment.spi.DatabaseDefaultSetupConfig.DEFAULT_DATABASE_USERNAME;

import io.quarkus.datasource.deployment.spi.DevServicesDatasourceContainerConfig;
import io.quarkus.datasource.deployment.spi.DevServicesDatasourceProvider.RunningDevServicesDatasource;
import io.quarkus.devservices.common.ContainerAddress;
import io.quarkus.devservices.common.DatasourceServiceConfigurator;

public class MilvusDatasourceServiceConfigurator implements DatasourceServiceConfigurator {

    public RunningDevServicesDatasource composeRunningService(ContainerAddress containerAddress,
            DevServicesDatasourceContainerConfig containerConfig) {
        String effectiveDbName = containerConfig.getDbName().orElse(DEFAULT_DATABASE_NAME);
        String effectiveUsername = containerConfig.getDbName().orElse(DEFAULT_DATABASE_USERNAME);
        String effectivePassword = containerConfig.getDbName().orElse(DEFAULT_DATABASE_PASSWORD);
        String jdbcUrl = getJdbcUrl(containerAddress, effectiveDbName);
        String reactiveUrl = getReactiveUrl(jdbcUrl);
        return new RunningDevServicesDatasource(
                containerAddress.getId(),
                jdbcUrl,
                reactiveUrl,
                effectiveUsername,
                effectivePassword);
    }

    @Override
    public String getJdbcPrefix() {
        return "milvus";
    }
}