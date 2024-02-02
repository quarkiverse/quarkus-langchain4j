package io.quarkiverse.langchain4j.runtime.jackson;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;

import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.UserMessage;

public class UserMessageDeserializer extends StdDeserializer<UserMessage> {

    public UserMessageDeserializer() {
        super(UserMessage.class);
    }

    @Override
    public UserMessage deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {

        String text = null;
        String name = null;
        List<Content> contents = null;
        while (p.nextToken() != JsonToken.END_OBJECT) {
            String key = p.getCurrentName();
            switch (key) {
                case "text":
                    text = p.getText();
                    break;
                case "name":
                    name = p.getText();
                    break;
                case "contents":
                    if (p.currentToken() == JsonToken.FIELD_NAME) {
                        p.nextToken();
                    }
                    if (p.currentToken() != JsonToken.START_ARRAY) {
                        throw ValueInstantiationException.from(p,
                                "Cannot construct instance of `dev.langchain4j.data.message.UserMessage`, problem: expected `"
                                        + p.currentToken() + "` to be start of array",
                                ctxt.constructType(UserMessage.class));
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
                    ctxt.constructType(
                            UserMessage.class));
        }
    }
}
