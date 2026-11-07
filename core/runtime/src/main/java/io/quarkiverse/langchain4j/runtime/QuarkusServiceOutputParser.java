package io.quarkiverse.langchain4j.runtime;

import static dev.langchain4j.service.TypeUtils.getRawClass;

import java.lang.reflect.Type;

import dev.langchain4j.service.output.ServiceOutputParser;
import io.smallrye.mutiny.Multi;

public class QuarkusServiceOutputParser extends ServiceOutputParser {

    @Override
    public String outputFormatInstructions(Type returnType) {
        Class<?> rawClass = getRawClass(returnType);
        if (Multi.class.equals(rawClass)) {
            // when Multi is used as the return type, Multi<String> is the only supported type, thus we don't need want any formatting instructions
            return "";
        }
        return super.outputFormatInstructions(returnType);
    }
}
