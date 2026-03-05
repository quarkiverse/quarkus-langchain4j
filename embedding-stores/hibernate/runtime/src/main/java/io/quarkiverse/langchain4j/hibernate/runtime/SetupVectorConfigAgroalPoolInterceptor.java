package io.quarkiverse.langchain4j.hibernate.runtime;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import io.agroal.api.AgroalPoolInterceptor;
import io.quarkus.runtime.configuration.ConfigUtils;

/**
 * SetupVectorConfigAgroalPoolInterceptor intercept connection creation and add needed settings for vector setup
 */
public class SetupVectorConfigAgroalPoolInterceptor implements AgroalPoolInterceptor {

    HibernateEmbeddingStoreConfig config;
    String setupSql;

    public SetupVectorConfigAgroalPoolInterceptor(HibernateEmbeddingStoreConfig config, String setupSql) {
        this.config = config;
        this.setupSql = setupSql;
    }

    @Override
    public void onConnectionCreate(Connection connection) {
        boolean setupVectorConfig = ConfigUtils.isProfileActive("dev") || ConfigUtils.isProfileActive("test")
                || this.config.setupVectorConfig();
        if (setupVectorConfig) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(setupSql);
            } catch (SQLException exception) {
                if (exception.getMessage().contains("could not open extension control file")) {
                    throw new RuntimeException(
                            "The PostgreSQL server does not seem to support pgvector."
                                    + "If using containers we suggest to use pgvector/pgvector:pg16 image");
                } else {
                    throw new RuntimeException(exception);
                }
            }
        }
    }
}
