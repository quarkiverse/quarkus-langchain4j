package io.quarkiverse.langchain4j.agentic.runtime.jackson;

import com.fasterxml.jackson.databind.module.SimpleDeserializers;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.module.SimpleSerializers;

import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;

/**
 * Jackson module that teaches an {@link com.fasterxml.jackson.databind.ObjectMapper} how to (de)serialize a
 * langchain4j {@link AgenticScope} by delegating to {@link dev.langchain4j.agentic.scope.AgenticScopeSerializer}.
 * <p>
 * Follows the same {@code SimpleModule} convention as the core {@code QuarkusLangChain4jModule}.
 */
public class AgenticScopeJacksonModule extends SimpleModule {

    public static final AgenticScopeJacksonModule INSTANCE = new AgenticScopeJacksonModule();

    @Override
    public String getModuleName() {
        return "QuarkusLangChain4jAgenticScopeModule";
    }

    @Override
    public void setupModule(SetupContext context) {
        SimpleSerializers serializers = new SimpleSerializers();
        serializers.addSerializer(AgenticScope.class, new AgenticScopeJsonSerializer());
        context.addSerializers(serializers);

        AgenticScopeJsonDeserializer deserializer = new AgenticScopeJsonDeserializer();
        SimpleDeserializers deserializers = new SimpleDeserializers();
        deserializers.addDeserializer(AgenticScope.class, deserializer);
        deserializers.addDeserializer(DefaultAgenticScope.class, deserializer);
        context.addDeserializers(deserializers);
    }
}
