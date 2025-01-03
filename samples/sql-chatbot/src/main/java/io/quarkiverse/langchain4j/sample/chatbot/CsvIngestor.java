package io.quarkiverse.langchain4j.sample.chatbot;

import org.apache.camel.builder.endpoint.EndpointRouteBuilder;
import org.apache.camel.model.dataformat.BindyType;

public class CsvIngestor extends EndpointRouteBuilder {
    @Override
    public void configure() throws Exception {
        // Continuously monitor the directory `src/main/resources/data` for any incoming file named `movies.csv`
        // The file is not deleted after processing because we set `noop(true)`
        // This is intentional for example purpose to keep the file in the directory
        from(file("src/main/resources/data?fileName=movies.csv")
                    .noop(true))
                // Convert the contents of the CSV file into a list of `Movie` objects using the Bindy Data Format
                .unmarshal().bindy(BindyType.Csv, Movie.class)
                /// Save the list of `Movie` objects to the database using the JPA producer
                // Note: The `entityType` parameter is set to `java.util.List` because the JPA producer
                // expects a single entity, and the CSV file has been transformed into a list of `Movie` objects
                .to(jpa(Movie.class.getName()+"?entityType=java.util.List"));
    }
}
