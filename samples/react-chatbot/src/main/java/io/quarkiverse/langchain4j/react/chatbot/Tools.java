package io.quarkiverse.langchain4j.react.chatbot;

import java.util.stream.Collectors;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.watsonx.ai.tool.builtin.GoogleSearchTool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class Tools {

    @Inject
    GoogleSearchTool googleSearchService;

    @Inject
    ObjectMapper objectMapper;

    public String googleSearch(String input) throws JsonProcessingException {
        return objectMapper.writeValueAsString(googleSearchService.search(input));
    }

    public String webCrawler(String url) {
        try {

            Document doc = Jsoup.connect(url).get();
            String pageContent = doc.select("p").stream()
                .map(Element::text)
                .collect(Collectors.joining("\n"));

            return """
                Title: %s
                Page content: %s
                """.formatted(doc.title(), pageContent);

        } catch (Exception e) {
            return "Error crawling " + url + ": " + e.getMessage();
        }
    }
}
