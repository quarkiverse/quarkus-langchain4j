package io.quarkiverse.langchain4j.pgvector;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import com.pgvector.PGvector;

import io.agroal.api.AgroalPoolInterceptor;
import io.quarkus.logging.Log;

/**
 * PgVectorAgroalPoolInterceptor intercept connection creation and add needed settings for pgvector
 */
public class PgVectorAgroalPoolInterceptor implements AgroalPoolInterceptor {
    @Override
    public void onConnectionCreate(Connection connection) {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE EXTENSION IF NOT EXISTS vector");
            PGvector.addVectorType(connection);
            Log.infof("Connection %s created with pgvector settings.", connection);
        } catch (SQLException exception) {
            if (exception.getMessage().contains("could not open extension control file")) {
                Log.error(
                        "The PostgreSQL server does not seem to support pgvector."
                                + "If using containers we suggest to use pgvector/pgvector:pg16 image");
            } else {
                throw new RuntimeException(exception);
            }
        }
    }
}
