package io.quarkiverse.langchain4j.mcp.runtime;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.quarkiverse.langchain4j.RegisterAiService;

/**
 * When used on a method of an AiService annotated with {@link RegisterAiService}, the method will use the tools
 * provided by the MCP servers named in {@code value}
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD })
public @interface McpToolBox {

    /**
     * MCP servers to use. In case no {@code value} is provided it will use all the MCP servers available.
     */
    String[] value() default {};
}
