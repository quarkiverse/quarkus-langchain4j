package io.quarkiverse.langchain4j.sample.chatbot;

import org.apache.camel.builder.endpoint.EndpointRouteBuilder;
import org.apache.camel.model.dataformat.BindyType;

public class CsvIngestor extends EndpointRouteBuilder {
    @Override
    public void configure() throws Exception {
        from(file("src/main/resources/data?fileName=movies.csv").noop(true))
                .unmarshal().bindy(BindyType.Csv, Movie.class)
                .to(jpa(Movie.class.getName()+"?entityType=java.util.List"));
    }
}
