package io.quarkiverse.langchain4j.agentic.runtime.observability;

import java.util.Collections;
import java.util.Set;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;
import org.jboss.logging.Logger;

import io.quarkus.arc.Arc;

@Readiness
public class AgentHealthCheck implements HealthCheck {

    private static final Logger log = Logger.getLogger(AgentHealthCheck.class);

    private static volatile Set<String> rootAgentClassNames = Collections.emptySet();

    public static void setRootAgentClassNames(Set<String> classNames) {
        rootAgentClassNames = Collections.unmodifiableSet(classNames);
    }

    @Override
    public HealthCheckResponse call() {
        HealthCheckResponseBuilder builder = HealthCheckResponse.named("Agent readiness");
        if (rootAgentClassNames.isEmpty()) {
            return builder.up().build();
        }
        boolean allUp = true;
        for (String className : rootAgentClassNames) {
            try {
                Class<?> clazz = Class.forName(className, true, Thread.currentThread().getContextClassLoader());
                Object agent = Arc.container().select(clazz).get();
                if (agent != null) {
                    builder.withData(className, "UP");
                } else {
                    builder.withData(className, "NOT FOUND");
                    allUp = false;
                }
            } catch (Exception e) {
                log.warnf("Agent health check failed for %s: %s", className, e.getMessage());
                builder.withData(className, "ERROR: " + e.getMessage());
                allUp = false;
            }
        }
        return allUp ? builder.up().build() : builder.down().build();
    }
}
