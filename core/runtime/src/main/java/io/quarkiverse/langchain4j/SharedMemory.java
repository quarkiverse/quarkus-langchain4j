package io.quarkiverse.langchain4j;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Allows multiple AI service methods (potentially belonging to different AI services) to share the same chat memory
 * when relying on the default memory id generation (i.e. when no parameter is annotated with
 * {@link dev.langchain4j.service.MemoryId}).
 * <p>
 * By default, the generated memory id has the form {@code <default-id>#<AI service FQN>.<method name>},
 * where {@code <default-id>} is provided by the active {@link io.quarkiverse.langchain4j.spi.DefaultMemoryIdProvider}.
 * When this annotation is present, the generated memory id instead has the form {@code <default-id>#<value>},
 * so all methods using the same {@code value} share the same chat memory.
 * <p>
 * Example:
 *
 * <pre>
 * &#64;RegisterAiService
 * public interface AgentOne {
 *
 *     &#64;SharedMemory("get-smart")
 *     String chat(&#64;UserMessage String msg);
 * }
 *
 * &#64;RegisterAiService
 * public interface Agent99 {
 *
 *     &#64;SharedMemory("get-smart")
 *     String chat(&#64;UserMessage String msg);
 * }
 * </pre>
 *
 * The annotation can be placed on the AI service interface, in which case it applies to all its methods.
 * When placed on both the interface and a method, the method level annotation takes precedence.
 * <p>
 * This annotation has no effect for methods that have a parameter annotated with {@link dev.langchain4j.service.MemoryId}.
 */
@Retention(RUNTIME)
@Target({ METHOD, TYPE })
public @interface SharedMemory {

    String DEFAULT = "$default";

    /**
     * The name of the shared memory. It replaces {@code <AI service FQN>.<method name>} in the generated memory id.
     */
    String value() default DEFAULT;
}
