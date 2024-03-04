package io.quarkiverse.langchain4j.sample.chatbot;

import jakarta.inject.Singleton;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
@Singleton
public interface MovieMuseSupport {

    @SystemMessage("""
            Create a SQL query to retrieve the data necessary to
            answer the user's question using data from the database.
            The database contains information about top rated movies from IMDB.
            The dialect is PostgreSQL and the relevant table is called 'movie'.
            Always include `movie_name` in the SELECT clause.

            The user might have not provided the movie name exactly, in that case
            try to correct it to the official movie name, or match it using a LIKE clause.

            The table has the following columns:
            - id (integer)
            - index (integer)
            - movie_name (string)
            - year_of_release (integer)
            - category (string)
            - run_time (integer, in minutes)
            - genre (string) - this is a comma-separated list of genres
            - imdb_rating (float)
            - votes (integer)
            - gross_total (integer, in millions of US dollars)

            Answer only with the query and nothing else.
            """)
    String createSqlQuery(@UserMessage String question);

}
