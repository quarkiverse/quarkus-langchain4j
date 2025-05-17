package io.quarkiverse.langchain4j.react.chatbot;

import java.util.Map;
import java.util.List;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

@Path("/")
public class ChatBotResource {

    @Inject
    ReActAgent react;

    @Inject
    Tools tools;

    @POST
    @Path("/react")
    public String react(String question) throws Exception {
        Log.infof("❓ QUESTION: %s", question);
        String toolResult = null;

        while (true) {

            var response = react.answer("fake", question, toolResult);

            if (response.thought() != null) {
                Log.infof("🧠 Thinking: %s", response.thought());
            }

            if (response.finalAnswer() != null) {
                Log.infof("✅ Final Answer: %s", response.finalAnswer());
                return response.finalAnswer();
            }

            Log.infof("🔨 Action: %s", response.action());
            Log.infof("🔨 Action Input: %s", response.args());

            Map<String, Object> args = response.args();

            toolResult = switch(response.action()) {
                case "googleSearch" -> tools.googleSearch((String) args.get("search"));
                case "webCrawler" -> tools.webCrawler((String) args.get("url"));
                default -> throw new IllegalArgumentException("Unknown action: " + response.action());
            };
        }
    }

}
