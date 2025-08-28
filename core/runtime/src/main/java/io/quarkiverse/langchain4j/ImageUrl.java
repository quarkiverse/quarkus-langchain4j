package io.quarkiverse.langchain4j;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * This annotation is useful when an AiService is meant to describe an image as the value of the method parameter annotated
 * with @ImageUrl
 * will be used as an {@link dev.langchain4j.data.message.ImageContent}.
 * <p>
 * <p>
 * The following code contains an example of how this can be used:
 *
 * <pre>
 * {@code
 * @RegisterAiService(chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
 * public interface ImageDescriber {
 *
 *     @UserMessage("This is image was reported on a GitHub issue. If this is a snippet of Java code, please respond"
 *             + " with only the Java code. If it is not, respond with 'NOT AN IMAGE'")
 *     Report describe(@ImageUrl String url);
 * }
 * </pre>
 *
 * There can be at most one instance of {@code ImageUrl} per method and the supported types are the following:
 * <ul>
 * <li>String</li>
 * <li>URL</li>
 * <li>URI</li>
 * <li>dev.langchain4j.data.image.Image</li>
 * </ul>
 *
 */
@Retention(RUNTIME)
@Target({ PARAMETER })
public @interface ImageUrl {

}
