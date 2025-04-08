package io.quarkiverse.langchain4j.mcp.runtime.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import org.eclipse.microprofile.config.spi.ConfigSource;
import org.eclipse.microprofile.config.spi.ConfigSourceProvider;

import io.quarkiverse.langchain4j.mcp.runtime.McpRecorder;
import io.smallrye.config.PropertiesConfigSource;

public class ClaudeDesktopConfigSourceProvider implements ConfigSourceProvider {
    @Override
    public Iterable<ConfigSource> getConfigSources(ClassLoader forClassLoader) {
        if (McpRecorder.claudeConfigContents.isEmpty()) {
            return List.of();
        }

        Map<String, String> configMap = new HashMap<>();
        McpRecorder.claudeConfigContents.forEach(new BiConsumer<>() {
            @Override
            public void accept(String clientName, LocalLaunchParams localLaunchParams) {
                String effectiveClientName = clientName.contains(".") ? "\"" + clientName + "\"" : clientName;
                if (!localLaunchParams.command().isEmpty()) {
                    configMap.put(String.format("quarkus.langchain4j.mcp.%s.command", effectiveClientName),
                            String.join(",", localLaunchParams.command()));
                }
                localLaunchParams.envVars().forEach(new BiConsumer<>() {
                    @Override
                    public void accept(String envVar, String value) {
                        configMap.put(String.format("quarkus.langchain4j.mcp.%s.environment.%s", effectiveClientName, envVar),
                                value);
                    }
                });
            }
        });

        return List.of(new PropertiesConfigSource(configMap, "ClaudeConfigSource"));
    }
}
