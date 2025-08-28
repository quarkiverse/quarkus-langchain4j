package io.quarkiverse.langchain4j;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * This annotation is useful when an AiService is meant to describe an audio as the value of the method parameter annotated
 * with @AudioUrl
 * will be used as an {@link dev.langchain4j.data.message.AudioContent}.
 * <p>
 * <p>
 * The following code contains an example of how this can be used:
 *
 * <pre>
 * {@code
 * @RegisterAiService(chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
 * public interface AudioDescriber {
 *
 *     @UserMessage("Describe the audio")
 *     Report describe(@AudioUrl String url);
 * }
 * </pre>
 *
 * There can be at most one instance of {@code AudioUrl} per method and the supported types are the following:
 * <ul>
 * <li>String</li>
 * <li>URL</li>
 * <li>URI</li>
 * <li>dev.langchain4j.data.audio.Audio</li>
 * </ul>
 *
 */
@Retention(RUNTIME)
@Target({ PARAMETER })
public @interface AudioUrl {

}
