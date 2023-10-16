package io.quarkiverse.langchain4j.runtime.jackson;

import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import dev.ai4j.openai4j.completion.CompletionChoice;
import dev.ai4j.openai4j.completion.CompletionResponse;
import dev.ai4j.openai4j.shared.Usage;

//TODO: figure out a way to get rid of this
public class CompletionResponseDeserializer extends StdDeserializer<CompletionResponse> {

    public CompletionResponseDeserializer() {
        super(CompletionResponse.class);
    }

    protected CompletionResponseDeserializer(Class<?> vc) {
        super(vc);
    }

    @Override
    public CompletionResponse deserialize(JsonParser p, DeserializationContext ctxt)
            throws IOException, JacksonException {

        try {
            p.nextToken();
        } catch (JsonParseException e) {
            if (e.getMessage().contains("DONE")) {
                return null;
            }
            throw e;
        }
        String id = null;
        Integer created = null;
        String model = null;
        List<CompletionChoice> choices = null;
        Usage usage = null;
        while (p.nextToken() != JsonToken.END_OBJECT) {
            String name = p.getCurrentName();
            if ("id".equals(name)) {
                p.nextToken();
                id = p.getText();
            } else if ("created".equals(name)) {
                p.nextToken();
                created = p.getIntValue();
            } else if ("model".equals(name)) {
                p.nextToken();
                model = p.getText();
            } else if ("choices".equals(name)) {
                p.nextToken();
                choices = ctxt.readValue(p,
                        ctxt.getTypeFactory().constructCollectionType(List.class, CompletionChoice.class));
            } else if ("usage".equals(name)) {
                p.nextToken();
                usage = ctxt.readValue(p, Usage.class);
            }
        }

        return CompletionResponse.builder().id(id).created(created).model(model).choices(choices).usage(usage).build();
    }
}
