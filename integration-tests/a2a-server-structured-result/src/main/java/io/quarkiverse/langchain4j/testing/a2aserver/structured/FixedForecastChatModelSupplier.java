package io.quarkiverse.langchain4j.testing.a2aserver.structured;

import java.util.function.Supplier;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * Answers with the JSON of one fixed {@link Forecast}. Supplying the model keeps the application free of a model
 * provider, and so of the network, while still letting the AI Service parse a real answer into the record.
 */
public class FixedForecastChatModelSupplier implements Supplier<ChatModel> {

    private static final String FORECAST_JSON = """
            {
              "city": "LA",
              "at": { "latitude": 34.05, "longitude": -118.24 },
              "days": [
                { "day": "Monday", "temperature": 25 },
                { "day": "Tuesday", "temperature": 27 }
              ]
            }""";

    @Override
    public ChatModel get() {
        return new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest request) {
                return ChatResponse.builder().aiMessage(new AiMessage(FORECAST_JSON)).build();
            }
        };
    }
}
