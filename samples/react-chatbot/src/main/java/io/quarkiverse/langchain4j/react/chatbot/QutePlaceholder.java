package io.quarkiverse.langchain4j.react.chatbot;

import io.quarkus.qute.TemplateGlobal;

public class QutePlaceholder {

    @TemplateGlobal
    public static String tools() {
        return """
            [
                {
                    "type": "function",
                    "function": {
                        "name": "googleSearch",
                        "description": "Use this tool to perform a web search on the internet to gather general or up-to-date information about a topic, event, or question.",
                        "parameters": {
                        "type": "object",
                        "properties": {
                            "search": {
                                "type": "string",
                                "description": "The exact query or topic to look up on the internet using a search engine."
                            }
                        },
                        "required": ["search"]
                        }
                    }
                },
                {
                    "type": "function",
                    "function": {
                        "name": "webCrawler",
                        "description": "Use this tool to extract detailed content directly from a specific webpage, given its full URL. Useful for reading or analyzing site content.",
                        "parameters": {
                        "type": "object",
                        "properties": {
                            "url": {
                                "type": "string",
                                "description": "The full URL of the webpage from which to extract content (e.g., https://example.com/page)."
                            }
                        },
                        "required": ["url"]
                        }
                    }
                }
            ]""";
    }
}
