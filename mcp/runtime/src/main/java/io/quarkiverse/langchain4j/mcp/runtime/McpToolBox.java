package io.quarkiverse.langchain4j.mcp.runtime;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import dev.langchain4j.service.tool.ToolProvider;
import io.quarkiverse.langchain4j.RegisterAiService;

/**
 * When used on a method of an AiService annotated with {@link RegisterAiService}, the method will use the tools
 * provided by the MCP servers named in {@code value}. If no name is provided than the method will automatically
 * use all the MCP servers available.
 * </p>
 * Note that the filtering of the named MCP servers is possible only if the MCP extension is allowed to automatically
 * generate a {@link ToolProvider} that is wired up to all the configured MCP clients,
 * i.e. the {@code quarkus.langchain4j.mcp.generate-tool-provider} property is set to true (which is the default value).
 * Conversely, if the AI service uses a custom {@link ToolProvider} than this annotation will have no effect and the
 * wiring of specific MCP clients will have to be encoded in the {@link ToolProvider} itself.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD })
public @interface McpToolBox {

    /**
     * MCP servers to use. In case no {@code value} is provided it will use all the MCP servers available.
     */
    String[] value() default {};
}
