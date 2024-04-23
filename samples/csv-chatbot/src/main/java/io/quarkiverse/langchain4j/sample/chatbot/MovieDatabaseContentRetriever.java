package io.quarkiverse.langchain4j.sample.chatbot;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import io.quarkus.logging.Log;
import io.vertx.core.json.JsonObject;

@ApplicationScoped
public class MovieDatabaseContentRetriever implements ContentRetriever {

    @Inject
    MovieMuseSupport support;

    @Inject
    DataSource dataSource;

    @Override
    public List<Content> retrieve(Query query) {
        String question = query.text();
        String sqlQuery = support.createSqlQuery(question, MovieSchemaSupport.getSchemaString());
        if (sqlQuery.contains("```sql")) { // strip the formatting if it's there
            sqlQuery = sqlQuery.substring(sqlQuery.indexOf("```sql") + 6, sqlQuery.lastIndexOf("```"));
        }
        Log.info("Question to answer: " + question);
        Log.info("Supporting SQL query: " + sqlQuery);
        List<Content> results = new ArrayList<>();
        Log.info("Retrieved relevant movie data: ");
        try (Connection connection = dataSource.getConnection()) {
            try (Statement statement = connection.createStatement()) {
                try (ResultSet resultSet = statement.executeQuery(sqlQuery)) {
                    while (resultSet.next()) {
                        JsonObject json = new JsonObject();
                        int columnCount = resultSet.getMetaData().getColumnCount();
                        for (int i = 1; i <= columnCount; i++) {
                            String columnName = resultSet.getMetaData().getColumnName(i);
                            json.put(columnName, resultSet.getObject(i));
                        }
                        Log.info("Item: " + json);
                        results.add(Content.from(json.toString()));
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return results;
    }
}
