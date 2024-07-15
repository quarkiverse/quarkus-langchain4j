package io.quarkiverse.langchain4j.pgvector;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import com.pgvector.PGvector;

import io.agroal.api.AgroalPoolInterceptor;
import io.quarkus.runtime.configuration.ProfileManager;

/**
 * PgVectorAgroalPoolInterceptor intercept connection creation and add needed settings for pgvector
 */
public class PgVectorAgroalPoolInterceptor implements AgroalPoolInterceptor {

    @Override
    public void onConnectionCreate(Connection connection) {
        try (Statement statement = connection.createStatement()) {
            if (ProfileManager.getLaunchMode().isDevOrTest()) {
                statement.executeUpdate("CREATE EXTENSION IF NOT EXISTS vector");
            }
            PGvector.addVectorType(connection);
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
