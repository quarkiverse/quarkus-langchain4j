package io.quarkiverse.langchain4j.runtime.jackson;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;

public class AiMessageDeserializer extends StdDeserializer<AiMessage> {
    public AiMessageDeserializer() {
        super(AiMessage.class);
    }

    @Override
    public AiMessage deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = p.getCodec().readTree(p);
        if (node.has("toolExecutionRequests")) {
            JsonNode toolExecutionRequestsNode = node.get("toolExecutionRequests");
            JsonParser toolExecutionsRequestsParser = toolExecutionRequestsNode.traverse();
            toolExecutionsRequestsParser.nextToken();
            List<ToolExecutionRequest> toolExecutionRequests = ctxt.readValue(toolExecutionsRequestsParser,
                    ctxt.getTypeFactory().constructCollectionType(ArrayList.class,
                            ToolExecutionRequest.class));
            return new AiMessage(toolExecutionRequests);
        } else {
            return new AiMessage(node.get("text").asText());
        }

    }
}
