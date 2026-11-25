package io.quarkiverse.langchain4j.runtime.aiservice;

import io.opentelemetry.api.common.AttributeKey;

/**
 * The attributes recorded on the span of an AI service invocation ({@code langchain4j.aiservices.*}).
 * <p>
 * The {@code gen_ai.*} keys follow the
 * <a href="https://github.com/open-telemetry/semantic-conventions-genai">OpenTelemetry GenAI semantic conventions</a>.
 * They are declared here because the constants in {@code opentelemetry-semconv-incubating} are deprecated now that the
 * GenAI conventions have moved to their own repository, which publishes no Java constants.
 */
public final class AiServiceSpanAttributes {

    /**
     * The name of the operation being performed, {@value #INVOKE_AI_SERVICE} for an AI service invocation.
     */
    public static final AttributeKey<String> GEN_AI_OPERATION_NAME = AttributeKey.stringKey("gen_ai.operation.name");

    /**
     * The {@link #GEN_AI_OPERATION_NAME} of an AI service invocation. The GenAI semantic conventions have no well-known
     * value for it, so it follows their {@code invoke_{framework_entrypoint}} form.
     */
    public static final String INVOKE_AI_SERVICE = "invoke_ai_service";

    /**
     * The arguments of the invocation, as a JSON object keyed by parameter name. Recorded when
     * {@code quarkus.langchain4j.tracing.include-ai-service-arguments} is enabled.
     */
    public static final AttributeKey<String> ARGUMENTS = AttributeKey.stringKey("langchain4j.aiservice.arguments");

    /**
     * The result of the invocation, as-is for a {@code String} and as JSON otherwise. Recorded when
     * {@code quarkus.langchain4j.tracing.include-ai-service-result} is enabled.
     */
    public static final AttributeKey<String> RESULT = AttributeKey.stringKey("langchain4j.aiservice.result");

    /**
     * The key of the object that replaces an argument that cannot be serialized to JSON. Its value is the fully
     * qualified name of the argument's class.
     */
    public static final String UNSERIALIZABLE_MARKER = "_unserializable";

    private AiServiceSpanAttributes() {
    }
}
