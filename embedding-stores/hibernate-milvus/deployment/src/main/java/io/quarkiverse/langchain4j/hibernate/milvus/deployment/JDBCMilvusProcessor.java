package io.quarkiverse.langchain4j.hibernate.milvus.deployment;

import io.quarkus.agroal.spi.JdbcDriverBuildItem;
import io.quarkus.datasource.deployment.spi.DefaultDataSourceDbKindBuildItem;
import io.quarkus.datasource.deployment.spi.DevServicesDatasourceConfigurationHandlerBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

public final class JDBCMilvusProcessor {

    public static final String DATABASE_KIND = "milvus";
    public static final String FEATURE = "jdbc-milvus";
    public static final String DRIVER_CLASS = "org.hibernate.milvus.jdbc.internal.MilvusDriver";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void registerDriver(BuildProducer<JdbcDriverBuildItem> jdbcDriver) {
        jdbcDriver.produce(new JdbcDriverBuildItem(
                DATABASE_KIND, DRIVER_CLASS, null));
    }

    @BuildStep
    DevServicesDatasourceConfigurationHandlerBuildItem devDbHandler() {
        return DevServicesDatasourceConfigurationHandlerBuildItem.jdbc(DATABASE_KIND);
    }

    @BuildStep
    void registerServiceBinding(BuildProducer<DefaultDataSourceDbKindBuildItem> dbKind) {
        dbKind.produce(new DefaultDataSourceDbKindBuildItem(DATABASE_KIND));
    }
}
