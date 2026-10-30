package io.quarkiverse.langchain4j.runtime.jackson;

import java.util.ArrayList;
import java.util.List;

import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.UserMessage;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;
import tools.jackson.databind.exc.ValueInstantiationException;

public class UserMessageDeserializer extends StdDeserializer<UserMessage> {

    public UserMessageDeserializer() {
        super(UserMessage.class);
    }

    @Override
    public UserMessage deserialize(JsonParser p, DeserializationContext ctxt) {

        String text = null;
        String name = null;
        List<Content> contents = null;
        while (p.nextToken() != JsonToken.END_OBJECT) {
            String key = p.currentName();
            switch (key) {
                case "text":
                    text = p.getString();
                    break;
                case "name":
                    name = p.getString();
                    break;
                case "contents":
                    // Move to the value if we're currently on the field name
                    p.nextToken();
                    if (p.currentToken() != JsonToken.START_ARRAY) {
                        throw ValueInstantiationException.from(p,
                                "Cannot construct instance of `dev.langchain4j.data.message.UserMessage`, problem: expected `"
                                        + p.currentToken() + "` to be start of array",
                                ctxt.getTypeFactory().constructType(UserMessage.class));
                    }
                    contents = new ArrayList<>();
                    while (p.nextToken() != JsonToken.END_ARRAY) {
                        contents.add(ctxt.readValue(p, Content.class));
                    }
                    break;
                default:
                    // ignore unknown properties
            }
        }

        if (text != null) {
            if (name == null) {
                return new UserMessage(text);
            } else {
                return new UserMessage(name, text);
            }
        } else if (contents != null) {
            if (name == null) {
                return new UserMessage(contents);
            } else {
                return new UserMessage(name, contents);
            }
        } else {
            throw ValueInstantiationException.from(p,
                    "Cannot construct instance of `dev.langchain4j.data.message.UserMessage`, problem: No `text` or `contents` field present",
                    ctxt.getTypeFactory().constructType(
                            UserMessage.class));
        }
    }
}
