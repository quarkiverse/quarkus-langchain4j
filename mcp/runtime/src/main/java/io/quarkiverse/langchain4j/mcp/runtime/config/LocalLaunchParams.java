package io.quarkiverse.langchain4j.mcp.runtime.config;

import java.util.List;
import java.util.Map;

public record LocalLaunchParams(List<String> command, Map<String, String> envVars) {
}
